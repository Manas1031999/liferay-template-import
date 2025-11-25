pipeline {
  agent any

  environment {
    // No SSH needed for local Windows deploy
    LIFERAY_DEPLOY = 'C:\\Users\\Bikram\\Downloads\\Manas Halder CV\\liferay-dxp-tomcat-2023.q4.4-1706605532\\liferay-dxp\\deploy\\'
    BUILD_CMD = 'gradlew.bat'
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
        // Use gradle wrapper for Windows
        bat "${env.BUILD_CMD} clean assemble --no-daemon"
      }
      post {
        success {
          archiveArtifacts artifacts: '**\\\\build\\\\libs\\\\*.war, **\\\\build\\\\libs\\\\*.jar', fingerprint: true
        }
      }
    }

    stage('Deploy to local Liferay') {
      steps {
        echo "Copying WAR(s) to Liferay deploy folder: ${env.LIFERAY_DEPLOY}"

        // Use PowerShell Copy-Item to handle spaces in path and wildcard copy
        bat """
          powershell -Command "Copy-Item -Path 'build\\\\libs\\\\*.war' -Destination '${env.LIFERAY_DEPLOY}' -Force -ErrorAction Stop"
        """

        echo "WAR(s) copied."
      }
    }

    stage('Wait & Smoke Test') {
      steps {
        echo "Waiting 12 seconds for Liferay to hot-deploy..."
        bat 'timeout /t 12 /nobreak > nul'

        echo "Running simple HTTP check..."
        // Use PowerShell to fetch status; fail the step if not 200
        bat """
          powershell -Command ^
            "$resp = Invoke-WebRequest -UseBasicParsing -Uri 'http://localhost:8080' -ErrorAction Stop; ^
             if ($resp.StatusCode -ne 200) { Write-Host 'HTTP not 200: ' + $resp.StatusCode; exit 1 } else { Write-Host 'OK: ' + $resp.StatusCode }"
        """
      }
    }
  }

  post {
    success {
      echo "Build, deploy and smoke test succeeded."
    }
    failure {
      echo "One or more stages failed. Check console output."
    }
    always {
      // collect test reports if any
      junit allowEmptyResults: true, testResults: '**\\\\build\\\\test-results\\\\**\\\\*.xml'
    }
  }
}
