pipeline {
    agent {
        kubernetes {
            yaml '''
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: helm-tools
    image: alpine/helm:3.12.0
    command:
    - sleep
    args:
    - infinity
'''
        }
    }

    parameters {
        string(name: 'REPO_URL', defaultValue: 'git@github.com:messsi10/socketio-test.git', description: 'Git repo SSH URL')
        string(name: 'BRANCH', defaultValue: 'main', description: 'Git branch')
        string(name: 'NAMESPACE', defaultValue: 'socketio-namespace', description: 'Kubernetes namespace')
        string(name: 'RELEASE_NAME', defaultValue: 'socketio', description: 'Helm release name')
        string(name: 'IMAGE_REPOSITORY', defaultValue: 'smeleshchyk/socketio-test', description: 'Docker image repository')
        string(name: 'IMAGE_TAG', defaultValue: '0.0.2', description: 'Docker image tag')
    }

    stages {
        stage('Cleanup') {
            steps {
                sh 'rm -rf *'
            }
        }
        stage('Install Helm') {
          steps {
            sh '''
              curl -fsSL https://get.helm.sh/helm-v3.20.0-linux-arm64.tar.gz -o helm.tgz
              tar -xzf helm.tgz
              mv linux-arm64/helm $HOME/helm
              chmod +x $HOME/helm
              export PATH=$HOME:$PATH
              helm version
            '''
          }
        }



        stage('Checkout') {
            steps {
                // Використовуємо нативний крок git, який у тебе вже запрацював
                git branch: params.BRANCH, 
                    credentialsId: 'github-ssh-key', 
                    url: params.REPO_URL
            }
        }

        stage('Helm install/upgrade') {
            steps {
                // Виконуємо команди всередині контейнера, де Є helm
                container('helm-tools') {
                    sh """
                        set -e
                        echo "Початок деплою у Minikube..."
                        
                        # Оскільки Chart.yaml лежить прямо в корені репо
                        helm upgrade --install "${params.RELEASE_NAME}" . \
                            --namespace "${params.NAMESPACE}" \
                            --create-namespace \
                            --set image.repository="${params.IMAGE_REPOSITORY}" \
                            --set image.tag="${params.IMAGE_TAG}" \
                            --wait
                    """
                }
            }
        }
    }
}