void setBuildStatus(String message, String state) {
    step([
            $class: "GitHubCommitStatusSetter",
            reposSource: [$class: "ManuallyEnteredRepositorySource", url: "https://github.com/my-org/my-repo"],
            contextSource: [$class: "ManuallyEnteredCommitContextSource", context: "ci/jenkins/build-status"],
            errorHandlers: [[$class: "ChangingBuildStatusErrorHandler", result: "UNSTABLE"]],
            statusResultSource: [ $class: "ConditionalStatusResultSource", results: [[$class: "AnyBuildResult", message: message, state: state]] ]
    ]);
}

def mavenBuildWithRelease() {
    node {
        checkout scm
        stage ('Build and Deploy') {
            if (env.BRANCH_NAME == 'master') {
                sshagent('GitHub') {
                    try {
                        sh 'mvn release:prepare'
                        sh 'mvn release:perform'
                    } catch (e) {
                        sh 'mvn release:rollback'
                    }
                }
            } else {
                sh 'mvn clean install'
            }
        }
        stage ('Publish Tests') {
            junit allowEmptyResults: true, testResults: 'target/surefire-reports/**/*.xml'
        }
        stage ('Update GitHub Status') {
            setBuildStatus("Build complete", "SUCCESS")
        }
    }
}
