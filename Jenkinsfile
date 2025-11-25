pipeline {
  agent any

  environment {
    SSH_CREDENTIALS = 'liferay-ssh'   // add this credential in Jenkins (SSH Username with private key)
    REMOTE_USER     = 'ubuntu'        // target server user
    REMOTE_HOST     = 'liferay.example.com'
    REMOTE_DEPLOY   = '/opt/liferay/tomcat-9.0.80/deploy/' // Liferay deploy dir or webapps
    // If you use Docker, set DEPLOY_METHOD='DOCKER' and DOCKER configs below
    DEPLOY_METHOD   = 'SCP'           // 'SCP' or 'DOCKER'
    DOCKER_REPO     = 'mydockerhubuser/liferay-custom'
    DOCKER_CREDS    = 'docker-creds'
    TAG             = "${env.BUILD_NUMBER}"
  }

  stages {
    stage('Checkout') { steps { checkout scm } }

    stage('Build') {
      steps {
        echo "Building using Gradle wrapper"
        bat 'gradlew.bat clean assemble --no-daemon'
      }
      post { success {
        archiveArtifacts artifacts: '**\\build\\libs\\*.jar, **\\build\\libs\\*.war', fingerprint: true
      } }
    }

    stage('Unit Tests') {
      steps {
        bat 'gradlew.bat test --no-daemon'
      }
      post { always { junit '**\\build\\test-results\\**\\*.xml' } }
    }

    stage('Deploy') {
      steps {
        script {
          if (env.DEPLOY_METHOD == 'SCP') {
            sshagent (credentials: [env.SSH_CREDENTIALS]) {
              bat "scp -o StrictHostKeyChecking=no build\\libs\\*.war ${REMOTE_USER}@${REMOTE_HOST}:${REMOTE_DEPLOY}"
              bat "ssh ${REMOTE_USER}@${REMOTE_HOST} 'sudo systemctl restart tomcat || echo restart-skipped'"
            }
          } else {
            // Docker flow
            bat "docker build -t %DOCKER_REPO%:%TAG% ."
            docker.withRegistry('', DOCKER_CREDS) {
              bat "docker push %DOCKER_REPO%:%TAG%"
              bat "docker tag %DOCKER_REPO%:%TAG% %DOCKER_REPO%:latest"
              bat "docker push %DOCKER_REPO%:latest"
            }
            sshagent (credentials: [env.SSH_CREDENTIALS]) {
              bat "ssh ${REMOTE_USER}@${REMOTE_HOST} 'docker pull %DOCKER_REPO%:%TAG% && docker-compose -f /opt/liferay/docker-compose.yml up -d web'"
            }
          }
        }
      }
    }

    stage('Smoke Test') {
      steps {
        echo 'Running smoke test'
        bat 'powershell -Command "(Invoke-WebRequest -UseBasicParsing http://'+REMOTE_HOST+':8080).StatusCode"'
      }
    }
  }

  post {
    success { echo "Build ${env.BUILD_NUMBER} Succeeded" }
    failure { echo "Build Failed - check Console Output" }
  }
}
