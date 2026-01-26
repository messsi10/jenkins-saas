def folders = ['_btbfStackV4.3', 'Delete env', 'DevOps', 'Drop CloudFlare cache', 'Mongo-snapshot', 'Suspend-an-environment']

folders.each { folderName ->
    folder(folderName)
}

pipelineJob("DevOps/test-job") {
    definition {
        cps {
            // Читаємо сусідній файл, який також скопіював Init-контейнер
            def pipelineFile = new File("/var/jenkins_home/dsl_scripts/test_pipeline.groovy")
            if (pipelineFile.exists()) {
                script(pipelineFile.text)
            } else {
                script("node { echo 'Error: pipeline file not found' }")
            }
            sandbox()
        }
    }
}

pipelineJob("Delete env/test-job-2") {
    definition {
        cps {
            // Читаємо сусідній файл, який також скопіював Init-контейнер
            def pipelineFile = new File("/var/jenkins_home/dsl_scripts/test_pipeline_2.groovy")
            if (pipelineFile.exists()) {
                script(pipelineFile.text)
            } else {
                script("node { echo 'Error: pipeline file not found' }")
            }
            sandbox()
        }
    }
}