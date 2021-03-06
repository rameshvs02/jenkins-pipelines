library changelog: false, identifier: "lib@master", retriever: modernSCM([
    $class: 'GitSCMSource',
    remote: 'https://github.com/Percona-Lab/jenkins-pipelines.git'
])

def operatingSystems = ['centos-6', 'centos-7', 'debian-9', 'debian-10', 'ubuntu-xenial', 'ubuntu-bionic', 'ubuntu-focal', 'rhel8']

pipeline {
  agent {
  label 'micro-amazon'
  }
  environment {
      PATH = '/usr/local/bin:/usr/bin:/usr/local/sbin:/usr/sbin:/home/ec2-user/.local/bin';
      MOLECULE_DIR = "molecule/pdmysql/${SCENARIO}";
  }
  parameters {
        choice(
            name: 'PLATFORM',
            description: 'For what platform (OS) need to test',
            choices: operatingSystems
        )
        choice(
            name: 'REPO',
            description: 'Repo for testing',
            choices: [
                'testing',
                'experimental',
                'release'
            ]
        )
        string(
            defaultValue: '8.0.19',
            description: 'PDMYSQL version for test',
            name: 'VERSION'
         )
        string(
            defaultValue: '2.0.14',
            description: 'Proxysql version for test',
            name: 'PROXY_VERSION'
         )
        string(
            defaultValue: '8.0.14',
            description: 'PXB version for test',
            name: 'PXB_VERSION'
         )
        string(
            defaultValue: '3.2.1',
            description: 'Percona toolkit version for test',
            name: 'PT_VERSION'
         )
        string(
            defaultValue: '3.1.4',
            description: 'Percona orchestrator version for test',
            name: 'ORCHESTRATOR_VERSION'
         )
        choice(
            name: 'SCENARIO',
            description: 'PDMYSQL scenario for test',
            choices: pdmysqlScenarios()
        )
  }
  options {
          withCredentials(moleculeDistributionJenkinsCreds())
          disableConcurrentBuilds()
  }
  stages {
    stage('Set build name'){
      steps {
                script {
                    currentBuild.displayName = "${env.BUILD_NUMBER}-${env.PLATFORM}-${env.SCENARIO}"
                }
            }
        }
    stage('Checkout') {
      steps {
            deleteDir()
            git poll: false, branch: 'master', url: 'https://github.com/Percona-QA/package-testing.git'
        }
    }
    stage ('Prepare') {
      steps {
          script {
              installMolecule()
            }
        }
    }
    stage ('Create virtual machines') {
      steps {
          script{
              moleculeExecuteActionWithScenario(env.MOLECULE_DIR, "create", env.PLATFORM)
            }
        }
    }
    stage ('Run playbook for test') {
      steps {
          script{
              moleculeExecuteActionWithScenario(env.MOLECULE_DIR, "converge", env.PLATFORM)
            }
        }
    }
    stage ('Start testinfra tests') {
      steps {
            script{
              moleculeExecuteActionWithScenario(env.MOLECULE_DIR, "verify", env.PLATFORM)
            }
            junit "${MOLECULE_DIR}/molecule/${PLATFORM}/report.xml"
        }
    }
    stage ('Start Cleanup ') {
      steps {
          script {
              moleculeExecuteActionWithScenario(env.MOLECULE_DIR, "cleanup", env.PLATFORM)
            }
        }
    }
  }
  post {
    always {
          script {
             moleculeExecuteActionWithScenario(env.MOLECULE_DIR, "destroy", env.PLATFORM)
        }
    }
  }
}
