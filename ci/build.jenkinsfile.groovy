pipeline {
    agent none
    options {
        // Add a global timeout to make sure we dont get builds hanging forever
        timeout(time: 2, unit: 'HOURS')
    }
    parameters {
        booleanParam(name: 'deployToDev', defaultValue: false, description: 'Set to true to auto deploy successfully built version to dev')
    }
    // We set the env after we assign an executor so cannot use declarative step for environment GIT_COMMIT_SHORT
    // environment {
        // GIT_COMMIT_SHORT = ""
    // }
    stages {
        stage('Build application image') {
            agent any
            steps {
                script {
                    env.APP_VERSION = env.BRANCH_NAME.split('/')[-1]
                    env.GIT_COMMIT_SHORT = sh(
                        script: "printf \$(git rev-parse --short ${GIT_COMMIT})",
                        returnStdout: true
                    )
                }
                sh "sudo docker build -t localhost:5001/xendit-demo-nodejs:${env.APP_VERSION}-${env.GIT_COMMIT_SHORT} ."
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
                        sh "helm kubeval ./xendit-demo-nodejs -v 1.20.15 --strict --schema-location https://raw.githubusercontent.com/yannh/kubernetes-json-schema/master"
                    }
                }
                stage('Dry-run application Chart') {
                    agent any
                    steps {
                        sh "helm install xendit-demo-dev --debug --dry-run --set appVersion=${env.APP_VERSION}-${env.GIT_COMMIT_SHORT} ./xendit-demo-nodejs"
                    }
                }
                stage('Run Checkov scan for application chart manifests') {
                    agent any
                    steps {
                        sh """#!/bin/bash
set -o pipefail
checkov \
--directory ./xendit-demo-nodejs \
--framework helm \
--hard-fail-on CRITICAL \
--output junitxml | tee ${env.WORKSPACE}/checkov_helm.xml
"""
                    }
                    post {
                        always {
                            sh "ls -la ${env.WORKSPACE}"
                            junit(
                                allowEmptyResults: true,
                                skipMarkingBuildUnstable: true,
                                testResults: '*helm.xml'
                            )
                        }
                    }
                }
                stage('Run Checkov scan against application Dockerfile') {
                    agent any
                    steps {
                        sh """#!/bin/bash
set -o pipefail
checkov \
--file Dockerfile \
--framework dockerfile \
--hard-fail-on CRITICAL \
--output junitxml | tee ${env.WORKSPACE}/checkov_docker.xml
"""
                    }
                    post {
                        always {
                            sh "ls -la ${env.WORKSPACE}"
                            junit(
                                allowEmptyResults: true,
                                skipMarkingBuildUnstable: true,
                                testResults: '*docker.xml'
                            )
                        }
                    }
                }
            }
        }
        stage('Publish application image') {
            agent any
            steps {
                // NOTE: we only have 1 node so no need to try to build again here for simplicity just pushing
                sh "sudo docker push localhost:5001/xendit-demo-nodejs:${env.APP_VERSION}-${env.GIT_COMMIT_SHORT}"
            }
        }
        stage('Smoke deploy/test helm chart') {
            agent any
            when { changeRequest() }
            steps {
                sh """
helm upgrade \
--namespace myapp \
--create-namespace \
--install smokedeploy-xendit-demo-${env.CHANGE_ID} \
--wait \
./xendit-demo-nodejs -f ./xendit-demo-nodejs/values.yaml
"""
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
                build job: '../../deploy_nodeapp', parameters: [[$class: 'StringParameterValue', name: 'VERSION', value: "${env.APP_VERSION}"]]
            }
        }
    }
}
