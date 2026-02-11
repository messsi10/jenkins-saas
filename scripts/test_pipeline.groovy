pipeline {
    agent {
        kubernetes {
            yaml '''
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: helm-tools
    image: dtzar/helm-kubectl:3.12.0
    command:
    - sleep
    args:
    - infinity
'''
        }
    }

    parameters {
        string(name: 'HELM_REPO_URL', defaultValue: 'git@github.com:messsi10/socketio-test.git', description: 'Git repo SSH URL')
        string(name: 'HELM_REPO_BRANCH', defaultValue: 'main', description: 'Git branch')
        string(name: 'CONFIG_REPO_URL', defaultValue: 'git@github.com:messsi10/configuration-repo.git', description: 'Config repo SSH URL (contains env values)')
        string(name: 'CONFIG_REPO_BRANCH', defaultValue: 'main', description: 'Config repo branch')
        choice(name: 'ENV', choices: ['dev', 'prod'], description: 'Deployment environment (selects values file)')
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
                script {
                    def branch = (params.HELM_REPO_BRANCH ?: 'main').trim()
                    echo "Checkout Helm repo: ${params.HELM_REPO_URL} (branch: ${branch})"
                    checkout([
                        $class: 'GitSCM',
                        branches: [[name: "*/${branch}"]],
                        userRemoteConfigs: [[
                            url: params.HELM_REPO_URL,
                            credentialsId: 'github-ssh-key'
                        ]]
                    ])
                }
            }
        }

        stage('Checkout config repo') {
            steps {
                dir('configuration-repo') {
                    script {
                        def branch = (params.CONFIG_REPO_BRANCH ?: 'main').trim()
                        echo "Checkout config repo: ${params.CONFIG_REPO_URL} (branch: ${branch})"
                        checkout([
                            $class: 'GitSCM',
                            branches: [[name: "*/${branch}"]],
                            userRemoteConfigs: [[
                                url: params.CONFIG_REPO_URL,
                                credentialsId: 'github-ssh-key'
                            ]]
                        ])
                    }
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
                            -f "configuration-repo/socketio-test-service/${params.ENV}-values.yaml" \
                            --wait

                        echo "Rolling restart workloads in ${params.NAMESPACE} for release ${params.RELEASE_NAME}..."
                        kubectl -n "${params.NAMESPACE}" rollout restart deployment "${params.RELEASE_NAME}" || true
                        kubectl -n "${params.NAMESPACE}" rollout restart statefulset "${params.RELEASE_NAME}" || true
                    """
                }
            }
        }
    }
}