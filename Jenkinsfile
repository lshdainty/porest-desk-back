pipeline {
    agent any
    parameters {
        choice(name: 'DEPLOY_ENV', choices: ['dev', 'prod'], description: '배포 환경')
        string(name: 'GIT_REF', defaultValue: 'main', description: '브랜치 또는 태그 (main, v1.0.0)')
    }
    environment {
        REPO_URL = "https://github.com/lshdainty/porest-desk-back.git"
        IMAGE_NAME = "porest-desk-back"
        SRC_DIR = "${env.POREST_BASE_DIR}/src/desk-back"
        ENV_FILE_DEV = "${env.POREST_BASE_DIR}/backend/dev/desk/dev.env"
        ENV_FILE_PROD = "${env.POREST_BASE_DIR}/backend/prod/desk/prod.env"
        CONTAINER_NAME = "desk-backend"
    }
    stages {
        stage('Checkout') {
            steps {
                dir("${SRC_DIR}") {
                    git branch: "${params.GIT_REF}",
                        url: "${REPO_URL}",
                        credentialsId: 'github-credentials'
                }
            }
        }
        stage('Docker Build') {
            steps {
                dir("${SRC_DIR}") {
                    withCredentials([usernamePassword(credentialsId: 'github-credentials', usernameVariable: 'GH_USER', passwordVariable: 'GH_TOKEN')]) {
                        sh 'docker build --build-arg GITHUB_ACTOR=$GH_USER --build-arg GITHUB_TOKEN=$GH_TOKEN -t ' + IMAGE_NAME + ':latest .'
                    }
                }
            }
        }
        stage('Deploy to Dev') {
            when { expression { params.DEPLOY_ENV == 'dev' } }
            steps {
                echo "Deploying Desk Backend to Development..."
                sh """
                    docker stop ${CONTAINER_NAME}-dev || true
                    docker rm ${CONTAINER_NAME}-dev || true
                    docker run -d --name ${CONTAINER_NAME}-dev \
                        --hostname ${CONTAINER_NAME}-dev \
                        --network ${env.DEV_NETWORK} \
                        --env-file ${ENV_FILE_DEV} \
                        -e SPRING_PROFILES_ACTIVE=dev \
                        -e LOKI_URL=${env.LOKI_URL} \
                        ${IMAGE_NAME}:latest
                """
            }
        }
        stage('Approval for Prod') {
            when { expression { params.DEPLOY_ENV == 'prod' } }
            steps {
                script {
                    input(
                        id: 'DeployToProd',
                        message: "운영 서버에 배포하시겠습니까?",
                        ok: '배포'
                    )
                }
            }
        }
        stage('Deploy to Prod') {
            when { expression { params.DEPLOY_ENV == 'prod' } }
            steps {
                echo "Deploying Desk Backend to Production..."
                sh """
                    docker stop ${CONTAINER_NAME}-prod || true
                    docker rm ${CONTAINER_NAME}-prod || true
                    docker run -d --name ${CONTAINER_NAME}-prod \
                        --hostname ${CONTAINER_NAME}-prod \
                        --network ${env.PROD_NETWORK} \
                        --env-file ${ENV_FILE_PROD} \
                        -e SPRING_PROFILES_ACTIVE=prod \
                        -e LOKI_URL=${env.LOKI_URL} \
                        ${IMAGE_NAME}:latest
                """
            }
        }
    }
}
