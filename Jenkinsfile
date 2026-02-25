pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    environment {
        GRADLE_OPTS = '-Dorg.gradle.daemon=false'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Environment') {
            steps {
                script {
                    if (isUnix()) {
                        sh 'java -version'
                        sh 'chmod +x gradlew'
                    } else {
                        bat 'java -version'
                    }
                }
            }
        }

        stage('Validate') {
            steps {
                script {
                    if (isUnix()) {
                        sh './gradlew --no-daemon :common:compileJava :fabric:compileJava :forge:compileJava'
                    } else {
                        bat 'gradlew.bat --no-daemon :common:compileJava :fabric:compileJava :forge:compileJava'
                    }
                }
            }
        }

        stage('Build Jars') {
            steps {
                script {
                    if (isUnix()) {
                        sh './gradlew --no-daemon :fabric:remapJar :forge:remapJar'
                    } else {
                        bat 'gradlew.bat --no-daemon :fabric:remapJar :forge:remapJar'
                    }
                }
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: 'fabric/build/libs/*.jar,forge/build/libs/*.jar', allowEmptyArchive: true
        }
    }
}
