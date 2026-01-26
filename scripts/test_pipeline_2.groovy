pipeline {
    agent any
    stages {
        stage('Deploy') {
            steps {
                echo "test job 2"
                echo "All scripts copied successfully by Init Container!"
            }
        }
    }
}