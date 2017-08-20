void setBuildStatus(String message, String state) {
    step([
            $class: "GitHubCommitStatusSetter",
            reposSource: [$class: "ManuallyEnteredRepositorySource", url: "https://github.com/my-org/my-repo"],
            contextSource: [$class: "ManuallyEnteredCommitContextSource", context: "ci/jenkins/build-status"],
            errorHandlers: [[$class: "ChangingBuildStatusErrorHandler", result: "UNSTABLE"]],
            statusResultSource: [ $class: "ConditionalStatusResultSource", results: [[$class: "AnyBuildResult", message: message, state: state]] ]
    ]);
}

def skip_build() {
    String GIT_COMMIT_EMAIL = sh (
            script: 'git --no-pager show -s --format=\'%ae\'',
            returnStdout: true
    ).trim()
    return GIT_COMMIT_EMAIL.contains("jenkins_ci")
}

def mavenBuildWithRelease() {
    node {
        checkout([
                $class: 'GitSCM',
                branches: "${env.BRANCH_NAME}",
                extensions: [
                        [$class: 'PruneStaleBranch'],
                        [$class: 'LocalBranch', localBranch: '**']
                ]])

        stage ('Build and Deploy') {
            if (env.BRANCH_NAME == 'master') {
                if (skip_build()) {
                    if (currentBuild.rawBuild.number > 1) {
                        echo 'Copying build state from previous build'
                        def index = 0
                        while (currentBuild.rawBuild.getPreviousBuild()?.getResult() == null && index < 60) {
                            sleep(10)
                            ++index
                        }
                        if (!hudson.model.Result.SUCCESS.equals(currentBuild.rawBuild.getPreviousBuild()?.getResult())) {
                            currentBuild.result = 'FAILURE'
                        }
                    } else {
                        echo 'First build - no previous result to copy'
                    }
                } else {
                    sshagent(['GitHub']) {
                        try {
                            sh 'mvn release:prepare'
                            sh 'mvn release:perform'
                        } catch (e) {
                            sh 'mvn release:rollback'
                            throw e
                        } finally {
                            junit allowEmptyResults: true, testResults: 'target/surefire-reports/**/*.xml'
                            setBuildStatus("Build complete", "SUCCESS")
                        }
                    }
                }
            } else {
                sh 'mvn clean install'
                junit allowEmptyResults: true, testResults: 'target/surefire-reports/**/*.xml'
                setBuildStatus("Build complete", "SUCCESS")
            }
        }
    }
}
