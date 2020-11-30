pipeline_timeout = 10

pipeline {
    parameters {
        string(
            defaultValue: 'https://github.com/percona/percona-xtrabackup',
            description: 'URL to PXB24 repository',
            name: 'PXB24_REPO',
            trim: true)
        string(
            defaultValue: 'percona-xtrabackup-2.4.20',
            description: 'Tag/Branch for PXC repository',
            name: 'PXB24_BRANCH',
            trim: true)
        choice(
            choices: 'min-xenial-x64',
            description: 'OS version for compilation',
            name: 'OS_LABEL')
        choice(
            choices: 'xtradb57',
            description: 'MySQL version for QA run',
            name: 'XTRABACKUP_TARGET')
        string(
            defaultValue: '5.7.31-34',
            description: 'Version of MS/PS/PXC Server which will be used for bootstrap.sh script',
            name: 'XTRABACKUP_TARGET_VERSION',
            trim: true)
        string(
            defaultValue: '',
            description: 'Pass an URL for downloading bootstrap.sh, If empty will use from repository you specified in PXB24_REPO',
            name: 'BOOTSTRAP_URL',
            trim: true)
        choice(
            choices: '/usr/bin/cmake',
            description: 'path to cmake binary',
            name: 'JOB_CMAKE')
        choice(
            choices: 'RelWithDebInfo\nDebug',
            description: 'Type of build to produce',
            name: 'CMAKE_BUILD_TYPE')
        string(
            defaultValue: '',
            description: 'cmake options',
            name: 'CMAKE_OPTS')
        string(
            defaultValue: '',
            description: 'make options, like VERBOSE=1',
            name: 'MAKE_OPTS')
        string(
            defaultValue: '-j 2',
            description: './run.sh options, for options like: -j N Run tests in N parallel processes, -T seconds, -x options  Extra options to pass to xtrabackup',
            name: 'XBTR_ARGS')
    }
    agent {
        label 'micro-amazon'
    }
    options {
        skipDefaultCheckout()
        skipStagesAfterUnstable()
        timeout(time: 6, unit: 'DAYS')
        buildDiscarder(logRotator(numToKeepStr: '200', artifactNumToKeepStr: '200'))
    }
    stages {
        stage('Ubuntu Xenial') {
            agent {
                    label OS_LABEL
                  }
            steps {
                git branch: 'master', url: 'https://github.com/rameshvs02/jenkins-pipelines'
                echo 'Checkout PXB24 sources'
                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: '24e68886-c552-4033-8503-ed85bbaa31f3', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                sh '''
                    # sudo is needed for better node recovery after compilation failure
                    # if building failed on compilation stage directory will have files owned by docker user
                    sudo git reset --hard
                    sudo git clean -xdf
                    sudo rm -rf sources
                    ./pxb/jenkins/checkout PXB24
                    mkdir $PWD/pxb/sources/pxb24/results
                    bash -x ./pxb/jenkins/build-binary-pxb24 $PWD/pxb/sources/pxb24/results $PWD/pxb/sources/pxb24
                    bash -x ./pxb/jenkins/test-binary-pxb24  $PWD/pxb/sources/pxb24/results
                    sudo chown -R $(id -u):$(id -g) $PWD/pxb/sources/pxb24/results $PWD/pxb/sources/pxb24/pxb24/storage/innobase/xtrabackup/src $PWD/pxb/sources/pxb24/pxb24/mysql-test
                '''
                }
                step([$class: 'JUnitResultArchiver', testResults: 'pxb/sources/pxb24/results/*.xml', healthScaleFactor: 1.0])
                archiveArtifacts 'pxb/sources/pxb24/results/*.xml,pxb/sources/pxb24/results/*.output,pxb/sources/pxb24/results/pxb24-test-xbtr_logs.tar.gz'
            } //End steps
        } //End stage Ubuntu Xenial
    }
    post {
        always {
            sh '''
                echo Finish: \$(date -u "+%s")
            '''
        }
    }
}
