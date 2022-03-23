pipeline {
    agent none
    options {
        // Add a global timeout to make sure we dont get builds hanging forever
        timeout(time: 2, unit: 'HOURS')
    }
    stages {
        stage('Build/Publish application image') {
            agent any
            steps {
                sh 'sudo docker build -t localhost:5001/xendit-demo-nodejs . && sudo docker push localhost:5001/xendit-demo-nodejs'
            }
        }
        //TODO parallel lint/test helm stuff, docker stuff
        stage('Tests/validations') {
            failFast false
            parallel {
                stage('Branch A') {
                    agent any
                    steps {
                        echo "On Branch A"
                    }
                }
                stage('Branch B') {
                    agent any
                    steps {
                        echo "On Branch B"
                    }
                }
            }
        }
    }
}
