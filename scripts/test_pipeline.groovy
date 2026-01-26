pipeline {
    agent any
    stages {
        stage('Deploy') {
            steps {
                echo "test job"
                echo "All scripts copied successfully by Init Container!"
            }
        }
    }
}