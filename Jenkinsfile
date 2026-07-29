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
        stage('Validate') {
            steps {
                script {
                    // 운영은 릴리즈 태그(vX.Y.Z)만 배포할 수 있다. main 등 브랜치는 거부.
                    if (params.DEPLOY_ENV == 'prod' && !(params.GIT_REF ==~ /v\d+\.\d+\.\d+/)) {
                        error "운영 배포는 릴리즈 태그(vX.Y.Z)만 허용됩니다. 선택된 값: ${params.GIT_REF}"
                    }
                }
            }
        }
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
                        if (params.DEPLOY_ENV == 'prod') {
                            // Validate 에서 vX.Y.Z 임이 보장됨 — 선택한 태그가 곧 버전
                            env.APP_VERSION = params.GIT_REF
                        } else {
                            // dev 는 태그 위여도 --long 으로 -0-g<hash> 를 붙여
                            // 운영 이미지(vX.Y.Z)와 이름이 절대 겹치지 않게 한다
                            env.APP_VERSION = sh(
                                script: 'git describe --tags --always --long 2>/dev/null || echo unknown',
                                returnStdout: true
                            ).trim()
                        }
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
