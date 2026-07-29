pipeline {
    agent any
    parameters {
        choice(name: 'DEPLOY_ENV', choices: ['dev', 'prod'], description: '배포 환경')
        gitParameter(
            name: 'GIT_REF',
            type: 'PT_BRANCH_TAG',
            branchFilter: 'origin/(.*)',
            tagFilter: '*',
            defaultValue: 'main',
            sortMode: 'DESCENDING_SMART',
            selectedValue: 'DEFAULT',
            quickFilterEnabled: true,
            description: '배포할 브랜치 또는 태그'
        )
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
                    checkout([$class: 'GitSCM',
                        branches: [[name: params.GIT_REF.startsWith('v') ? "refs/tags/${params.GIT_REF}" : "*/${params.GIT_REF}"]],
                        userRemoteConfigs: [[url: "${REPO_URL}", credentialsId: 'github-credentials']]
                    ])
                }
            }
        }
        stage('Resolve Version') {
            steps {
                dir("${SRC_DIR}") {
                    script {
                        // 태그 위면 v1.0.1, 태그 이후면 v1.0.1-3-gabc1234, 태그 없으면 커밋 해시
                        env.APP_VERSION = sh(
                            script: 'git describe --tags --always 2>/dev/null || echo unknown',
                            returnStdout: true
                        ).trim()
                        echo "APP_VERSION = ${env.APP_VERSION}"
                    }
                }
            }
        }
        stage('Docker Build') {
            steps {
                dir("${SRC_DIR}") {
                    withCredentials([usernamePassword(credentialsId: 'github-credentials', usernameVariable: 'GH_USER', passwordVariable: 'GH_TOKEN')]) {
                        sh 'docker build --build-arg GITHUB_ACTOR=$GH_USER --build-arg GITHUB_TOKEN=$GH_TOKEN -t ' + IMAGE_NAME + ':latest -t ' + IMAGE_NAME + ':' + env.APP_VERSION + ' .'
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
                        --restart unless-stopped \
                        --network ${env.DEV_NETWORK} \
                        --env-file ${ENV_FILE_DEV} \
                        -e SPRING_PROFILES_ACTIVE=dev \
                        -e LOKI_URL=${env.LOKI_URL} \
                        -e APP_VERSION=${env.APP_VERSION} \
                        ${IMAGE_NAME}:${env.APP_VERSION}
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
                        --restart unless-stopped \
                        --network ${env.PROD_NETWORK} \
                        --env-file ${ENV_FILE_PROD} \
                        -e SPRING_PROFILES_ACTIVE=prod \
                        -e LOKI_URL=${env.LOKI_URL} \
                        -e APP_VERSION=${env.APP_VERSION} \
                        ${IMAGE_NAME}:${env.APP_VERSION}
                """
            }
        }
    }
}
