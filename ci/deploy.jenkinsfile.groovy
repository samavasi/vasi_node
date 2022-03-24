#!/usr/bin/env groovy

//TODO add parameter to choose from available versions
//Maybe since monorepo same version strategy for all, so each commit becomes a version to deploy?

g

@Library('github.com/releaseworks/jenkinslib') _
def gv

pipeline {

    // Where to execute the pipeline script
    agent any
    parameters {
        choice(name: 'VERSION', choices: ['1.1.0', '1.2.0', '1.3.0'], description: 'Version of app/chart to deploy (monorepo setup so all maps to same version)')
        booleanParam(name: 'DEBUG_FLAG', defaultValue: false, description: 'Verbose output for helm deploy steps')
    }
    environment {
        NEW_VERSION = '1.0.1'
    }
    // Different pipeline stages
    stages {
        stage("init") {
            steps {
                script {
                    echo "Initializing groovy scripts..."
                    gv = load "script.groovy"
                    echo "Finished initializing groovy scripts"
                }
            }
        }

        stage("build frontend") {
            // Script executes command on Jenkins agent
            steps {
                script {
                    gv.buildApp()
                }
                dir('frontend') {
                    nodejs('Node-16.11') {
                        sh 'npm install'
                        sh 'npm run build'
                    }
                }
            }
        }

        stage("test frontend") {
            when {
                expression {
                    (env.BRANCH_NAME == 'dev' || env.BRANCH_NAME == 'main')  && params.executeTests == true
                }
            }
            steps {
                script {
                    gv.testApp()
                }
            }
        }

        stage("test backend") {
            when {
                expression {
                    (env.BRANCH_NAME == 'dev' || env.BRANCH_NAME == 'main')  && params.executeTests == true
                }
            }
            steps {
                script {
                    gv.testApp()
                }
                dir('frontend') {
                    nodejs('Node-16.11') {
                        sh 'npm run test'
                    }
                }
            }
        }

        stage("deploy frontend") {
            when {
                expression {
                    (env.BRANCH_NAME == 'main')
                }
            }
            steps {
                script {
                    gv.deployApp()
                }
                dir('frontend') {
                    withCredentials([[
                        $class: 'AmazonWebServicesCredentialsBinding',
                        credentialsId: "aws-admin2",
                        accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                        secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
                    ]]) {
                        AWS("s3 sync build/ s3://files.patrickdbustos.link")
                    }
                }
            }
        }
    }
}
