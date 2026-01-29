pipeline {
    agent any

    parameters {
        // Обов'язково використовуємо SSH URL: git@github.com:USER/REPO.git
        string(name: 'REPO_URL', defaultValue: 'git@github.com:smeleshchyk/socketio-test.git', description: 'Git repo SSH URL')
        string(name: 'BRANCH', defaultValue: 'main', description: 'Git branch')
        string(name: 'NAMESPACE', defaultValue: 'socketio-namespace', description: 'Kubernetes namespace')
        string(name: 'RELEASE_NAME', defaultValue: 'socketio', description: 'Helm release name')
    }

    stages {
        stage('Cleanup') {
            steps {
                sh 'rm -rf *'
            }
        }

        stage('Checkout') {
            steps {
                // Цей блок потребує плагіна "SSH Agent Plugin"
                sshagent(['github-ssh-key']) {
                    sh '''
                        mkdir -p repo
                        # Вимикаємо перевірку хоста через прапорець SSH
                        export GIT_SSH_COMMAND="ssh -o StrictHostKeyChecking=no"
                        git clone --single-branch --branch "${BRANCH}" "${REPO_URL}" repo
                    '''
                }
            }
        }

        stage('Helm install/upgrade') {
            steps {
                dir('repo') {
                    sh '''
                        set -e
                        echo "Deploying to Minikube..."
                        
                        if [ -d "./socketio-chart" ]; then
                            helm upgrade --install "${RELEASE_NAME}" ./socketio-chart \
                                --namespace "${NAMESPACE}" \
                                --create-namespace \
                                --wait
                        else
                            echo "Error: Directory ./socketio-chart not found!"
                            ls -R
                            exit 1
                        fi
                    '''
                }
            }
        }
    }
}