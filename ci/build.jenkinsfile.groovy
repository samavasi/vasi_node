pipeline {
    agent none
    options {
        // Add a global timeout to make sure we dont get builds hanging forever
        timeout(time: 2, unit: 'HOURS')
    }
    parameters {
        booleanParam(name: 'deployToDev', defaultValue: false, description: 'Set to true to auto deploy successfully built version to dev')
    }
    stages {
        stage('Build application image') {
            agent any
            //TODO get/set version (commitSha + semver?)
            steps {
                sh 'sudo docker build -t localhost:5001/xendit-demo-nodejs .'
            }
        }
        stage('Tests/validations') {
            failFast false
            parallel {
                stage('Helm Lint application chart') {
                    agent any
                    steps {
                        sh "helm lint --strict ./xendit-demo-nodejs"
                    }
                }
                stage('Run Kubeval for application chart manifests') {
                    agent any
                    steps {
                        sh "helm kubeval ./xendit-demo-nodejs -v 1.20.15 || true" //TODO fix Failed initalizing schema https://kubernetesjsonschema.dev
                    }
                }
                stage('Run Checkov scan for application chart manifests') {
                    agent any
                    steps {
                        sh "checkov --directory ./xendit-demo-nodejs --framework helm --hard-fail-on CRITICAL --output junitxml > ${env.WORKSPACE}/checkov_helm.xml"
                    }
                    post {
                        always {
                            junit "${env.WORKSPACE}/checkov_helm.xml"
                        }
                    }
                }
                stage('Run Checkov scan against application Dockerfile') {
                    agent any
                    steps {
                        sh "checkov --file Dockerfile --framework dockerfile --hard-fail-on CRITICAL --output junitxml > ${env.WORKSPACE}/checkov_docker.xml"
                    }
                    post {
                        always {
                            junit "${env.WORKSPACE}/checkov_docker.xml"
                        }
                    }
                }
            }
        }
        stage('Publish application image') {
            agent any
            steps {
                // NOTE: we only have 1 node so no need to try to build again here for simplicity just pushing
                sh 'sudo docker push localhost:5001/xendit-demo-nodejs'
            }
        }
        stage('Smoke deploy/test helm chart') {
            agent any
            when { changeRequest() }
            steps {
                sh "helm upgrade --namespace myapp --create-namespace --install smokedeploy-xendit-demo-${env.CHANGE_ID} --wait ./xendit-demo-nodejs -f ./xendit-demo-nodejs/values.yaml"
                sh "helm test smokedeploy-xendit-demo-${env.CHANGE_ID}"
            }
            post {
                always {
                    sh "helm uninstall smokedeploy-xendit-demo-${env.CHANGE_ID}"
                }
            }
        }
        stage('Deploy to dev') {
            when { expression { return params.deployToDev } }
            steps {
                echo "Deploying"
            }
        }
    }
}
