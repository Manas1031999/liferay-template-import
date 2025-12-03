pipeline {
  agent any

  environment {
    LIFERAY_DEPLOY = 'C:\\Users\\Bikram\\Downloads\\Manas Halder CV\\liferay-dxp-tomcat-2023.q4.4-1706605532\\liferay-dxp\\deploy\\'
    GRADLEW = 'gradlew.bat'
  }
  

  stages {

    stage('Checkout') {
      steps {
        checkout scm
      }
    }
    stage('Print SCM') {
      steps {
          script {
              echo groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(scm))
          }
      }
    }
    stage('Build') {
      steps {
        echo "Running Gradle build..."

      }
    }

    stage('Test') {
      steps {
        echo "Testing"
      }
    }

    stage('Deploy') {
      steps {
        echo "Deploying"
      }
    }
  }
}
