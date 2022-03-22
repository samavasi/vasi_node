pipeline {
    agent any
    options { skipDefaultCheckout() }
    parameters {
        choice(name: 'VERSION', choices: ['v1', 'v2', 'v3'], description: 'Version of app/chart to deploy (monorepo setup so everything maps to single version)')
        booleanParam(name: 'DEBUG_FLAG', defaultValue: false, description: 'Verbose output for helm deploy steps')
    }
    stages {
        stage("Checkout release branch version") {
            steps {
                checkout([$class: 'GitSCM',
                      		branches: [['name': "refs/heads/release/${VERSION}"]],
              						extensions: scm.extensions,
              						userRemoteConfigs: scm.userRemoteConfigs])
                script {
                    env.GIT_COMMIT_SHORT = sh(
                        script: "printf \$(git rev-parse --short HEAD)",
                        returnStdout: true
                    )
                    env.APP_VERSION = "${params.VERSION}-${env.GIT_COMMIT_SHORT}"
                }
            }
        }

        stage('Deploy xendit-demo-nodejs to dev') {
            steps {
                sh """
helm upgrade \
--namespace myapp \
--create-namespace \
--install xendit-demo-dev \
--wait \
./xendit-demo-nodejs \
-f ./xendit-demo-nodejs/values.yaml \
--set image.tag=${env.APP_VERSION}
"""
                sh "helm test --namespace myapp xendit-demo-dev"
            }
        }
    }
}
