pipeline {
  agent any

  environment {
    LIFERAY_DEPLOY_PATH = 'C:\\Users\\Bikram\\Downloads\\Manas Halder CV\\liferay-dxp-tomcat-2023.q4.4-1706605532\\liferay-dxp\\deploy\\'
  }
  stages {

    stage('Checkout') {
      steps {
        checkout scm
      }
    }
    stage('Build') {
      steps {
        echo "Running Gradle build..."
        bat '.\\gradlew.bat clean build --no-daemon'

      }
    }
    stage('Archive artifact') {
      steps {
        // adjust jar path if your project is multi-module
        archiveArtifacts artifacts: 'build\\libs\\*.jar', fingerprint: true
      }
    }
    stage('Deploy to Liferay (local)') {
      steps {
        script {
          def jars = findFiles(glob: 'build\\libs\\*.jar')
          if (jars.length == 0) {
            error "No jars found in build\\libs"
          }
          for (j in jars) {
            echo "Deploying ${j.path} -> ${env.LIFERAY_DEPLOY_PATH}"
            bat "copy /Y \"${j.path}\" \"${env.LIFERAY_DEPLOY_PATH}\\\""
          }
        }
      }
    }
    
  
  }
  post {
       success { echo "Build and deploy finished." }
      failure { echo "Build failed." }
    }
}
