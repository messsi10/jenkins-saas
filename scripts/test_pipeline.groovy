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
    }

    stages {
        stage('Cleanup') {
            steps {
                sh 'rm -rf *'
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
        stage('Load values.yaml') {
        steps {
            script {
            def v = readYaml file: 'values.yaml'        // або 'chart/values.yaml'
            env.IMAGE_TAG_FROM_VALUES = (v?.image?.tag ?: '0.0.1').toString()
            env.IMAGE_REPO_FROM_VALUES = (v?.image?.repository ?: 'smeleshchyk/socketio-test').toString()
                   }
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
                            --set image.repository="${env.IMAGE_REPO_FROM_VALUES}" \
                            --set image.tag="${env.IMAGE_TAG_FROM_VALUES}" \
                            --wait
                    """
                }
            }
        }
    }
}