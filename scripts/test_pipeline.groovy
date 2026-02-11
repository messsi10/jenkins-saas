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
        string(name: 'CONFIG_APP_DIR', defaultValue: 'socketio-test-service', description: 'Subdirectory in configuration-repo (e.g. socketio-test-service or socketio-test-service-2)')
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

                        echo "Ensuring External Secrets resources exist in namespace ${params.NAMESPACE}..."
                        kubectl get namespace "${params.NAMESPACE}" >/dev/null 2>&1 || kubectl create namespace "${params.NAMESPACE}"
                        # Apply in a stable order: SA -> SecretStore -> ExternalSecret
                        kubectl -n "${params.NAMESPACE}" apply -f "configuration-repo/${params.CONFIG_APP_DIR}/external-secrets/serviceaccount.yaml"
                        kubectl -n "${params.NAMESPACE}" apply -f "configuration-repo/${params.CONFIG_APP_DIR}/external-secrets/secretstore.yaml"
                        kubectl -n "${params.NAMESPACE}" apply -f "configuration-repo/${params.CONFIG_APP_DIR}/external-secrets/externalsecret.yaml"
                        kubectl -n "${params.NAMESPACE}" wait --for=condition=Ready \\
                          externalsecret/"${params.CONFIG_APP_DIR}-env" --timeout=2m
                        
                        # Оскільки Chart.yaml лежить прямо в корені репо
                        helm upgrade --install "${params.RELEASE_NAME}" . \
                            --namespace "${params.NAMESPACE}" \
                            --create-namespace \
                            -f "configuration-repo/${params.CONFIG_APP_DIR}/${params.ENV}-values.yaml" \
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