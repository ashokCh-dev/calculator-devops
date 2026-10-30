pipeline {
    agent any

    triggers {
        // Jenkins runs on localhost, so poll GitHub every ~2 minutes
        pollSCM('H/2 * * * *')
    }

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '15'))
        timeout(time: 30, unit: 'MINUTES')
    }

    environment {
        IMAGE_NAME   = 'ashokch11/calculator-devops'
        IMAGE_TAG    = "${env.BUILD_NUMBER}"
        PROXY_PORT   = '8090'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                sh 'git log -1 --oneline'
            }
        }

        stage('Build') {
            steps {
                sh 'chmod +x mvnw'
                sh './mvnw -B clean package -DskipTests'
            }
        }

        stage('Test') {
            steps {
                sh './mvnw -B test'
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                }
            }
        }

        stage('Docker Build') {
            steps {
                sh 'docker build -t $IMAGE_NAME:$IMAGE_TAG -t $IMAGE_NAME:latest .'
            }
        }

        stage('Push') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'dockerhub-creds',
                                                  usernameVariable: 'DOCKER_USER',
                                                  passwordVariable: 'DOCKER_PASS')]) {
                    sh '''
                        echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin
                        docker push $IMAGE_NAME:$IMAGE_TAG
                        docker push $IMAGE_NAME:latest
                    '''
                }
            }
            post {
                always {
                    sh 'docker logout || true'
                }
            }
        }

        stage('Deploy') {
            steps {
                // up -d only recreates containers whose image changed, so re-runs are safe
                sh 'docker compose up -d --remove-orphans'
                sh '''
                    echo "Waiting for the stack to become healthy..."
                    for i in $(seq 1 30); do
                        if curl -sf http://localhost:$PROXY_PORT/actuator/health | grep -q '"status":"UP"'; then
                            echo "Health check passed"
                            curl -s "http://localhost:$PROXY_PORT/api/v1/add?a=2&b=3"
                            echo
                            docker compose ps
                            exit 0
                        fi
                        sleep 3
                    done
                    echo "Deployment did not become healthy in time"
                    docker compose ps
                    docker compose logs --tail=50
                    exit 1
                '''
            }
        }
    }

    post {
        always {
            // clean up dangling layers from repeated image builds
            sh 'docker image prune -f || true'
        }
    }
}
