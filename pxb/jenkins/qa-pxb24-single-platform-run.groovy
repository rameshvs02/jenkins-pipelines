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
        password(
            defaultValueAsSecret: "",
            description: 'xbcloud pass',
            name: 'XBCLOUD_PASS')
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
                   echo ".........."
                   echo $XBCLOUD_PASS
                   echp "..........."
                   exit 1
                   if [ -f /usr/bin/yum ]; then
                       sudo -E yum -y erase percona-release || true
                       sudo find /etc/yum.repos.d/ -iname 'percona*' -delete
                       until sudo -E yum -y install epel-release; do
                           sudo yum clean all
                           sleep 1
                           echo "waiting"
                       done
                       until sudo -E yum -y makecache; do
                           sudo yum clean all
                           sleep 1
                           echo "waiting"
                       done

                       RHEL=$(rpm --eval %rhel)
                       sudo yum -y install wget
                       PKGLIST="libcurl-devel cmake make gcc gcc-c++ libev-devel openssl-devel"
                       PKGLIST="${PKGLIST} libaio-devel perl-DBD-MySQL vim-common ncurses-devel readline-devel"
                       PKGLIST="${PKGLIST} zlib-devel libgcrypt-devel bison perl-Digest-MD5"
                       PKGLIST="${PKGLIST} socat numactl-libs numactl"

                       if [[ ${RHEL} != 8 ]]; then
                           PKGLIST+=" python-sphinx python-docutils"
                       else
                           PKGLIST+=" python3-pip python3-setuptools python3-wheel wget ncurses-compat-libs lz4 lz4-devel"
                       fi

                       if [[ ${RHEL} -eq 6 ]]; then
                           sudo wget -O /etc/yum.repos.d/slc6-devtoolset.repo http://linuxsoft.cern.ch/cern/devtoolset/slc6-devtoolset.repo
                           sudo wget -O /etc/pki/rpm-gpg/RPM-GPG-KEY-cern https://raw.githubusercontent.com/cms-sw/cms-docker/master/slc6/RPM-GPG-KEY-cern
                           PKGLIST+=" devtoolset-2-gcc-c++ devtoolset-2-binutils"
                           PKGLIST+=" devtoolset-2-libasan-devel"
                           PKGLIST+=" devtoolset-2-valgrind devtoolset-2-valgrind-devel"
                       fi

                       until sudo -E yum -y install ${PKGLIST}; do
                           echo "waiting"
                           sleep 1
                       done

                       if [[ ${RHEL} -eq 8 ]]; then
                           sudo /usr/bin/pip3 install sphinx
                           sudo ln -sf /bin/python3 /bin/python
                       fi
                   fi
                   #
                   if [ -f /usr/bin/apt-get ]; then

                       sudo rm -f /etc/apt/sources.list.d/percona-dev.list
                       #
                       until sudo -E apt-get update; do
                           sleep 1
                           echo "waiting"
                       done
                       #
                       until sudo -E apt-get -y install lsb-release; do
                           sleep 1
                           echo "waiting"
                       done
                       #
                       sudo -E apt-get -y purge eatmydata || true
                       #
                       DIST=$(lsb_release -sc)

                       if [[ "$DIST" != 'focal' ]]; then
                           echo "deb http://jenkins.percona.com/apt-repo/ ${DIST} main" | sudo tee /etc/apt/sources.list.d/percona-dev.list
                           wget -q -O - http://jenkins.percona.com/apt-repo/8507EFA5.pub | sudo apt-key add -
                           wget -q -O - http://jenkins.percona.com/apt-repo/CD2EFD2A.pub | sudo apt-key add -
                       fi

                       until sudo -E apt-get update; do
                           sleep 1
                           echo "waiting"
                       done
                       #
                       PKGLIST="bison cmake devscripts debconf debhelper automake bison ca-certificates libcurl4-openssl-dev"
                       PKGLIST="${PKGLIST} cmake debhelper libaio-dev libncurses-dev libssl-dev libtool libz-dev"
                       PKGLIST="${PKGLIST} libgcrypt-dev libev-dev lsb-release python-docutils"
                       PKGLIST="${PKGLIST} build-essential rsync libdbd-mysql-perl libnuma1 socat librtmp-dev"
                       if [[ "$DIST" == 'focal' ]]; then
                           PKGLIST="${PKGLIST} python3-sphinx"
                       else
                           PKGLIST="${PKGLIST} python-sphinx"
                       fi

                       if [[ "$Host" == *"asan" ]] ; then
                           PKGLIST="${PKGLIST} libasan4 pkg-config"
                       fi
                       until sudo -E DEBIAN_FRONTEND=noninteractive apt-get -y install ${PKGLIST}; do
                           sleep 1
                           echo "waiting"
                       done
                       #
                       sudo -E apt-get -y install libreadline6 || true
                       sudo -E apt-get -y install libreadline6-dev || true
                       if [ -e /lib/x86_64-linux-gnu/libreadline.so.7 -a ! -e /lib/x86_64-linux-gnu/libreadline.so.6 ]; then
                           sudo ln -s /lib/x86_64-linux-gnu/libreadline.so.7 /lib/x86_64-linux-gnu/libreadline.so.6
                       fi

                       if [ -e /lib/x86_64-linux-gnu/libreadline.so -a ! -e /lib/x86_64-linux-gnu/libreadline.so.6 ]; then
                           sudo ln -s /lib/x86_64-linux-gnu/libreadline.so /lib/x86_64-linux-gnu/libreadline.so.6
                       fi

                       if [[ ${DIST} == 'focal' ]]; then
                           sudo ln -sf /usr/bin/python3 /usr/bin/python
                       fi

                   fi
                   # sudo is needed for better node recovery after compilation failure
                   # if building failed on compilation stage directory will have files owned by docker user
                   sudo git reset --hard
                   sudo git clean -xdf
                   sudo rm -rf sources
                   ./pxb/jenkins/checkout PXB24
                   mkdir $PWD/pxb/sources/pxb24/results
                   bash -x ./pxb/jenkins/build-binary-pxb24 $PWD/pxb/sources/pxb24/results $PWD/pxb/sources/pxb24
                   bash -x ./pxb/jenkins/test-binary-pxb24  $PWD/pxb/sources/pxb24/results
                   sudo chown -R $(id -u):$(id -g) $PWD/pxb/sources/pxb24/results $PWD/pxb/sources/pxb24/storage/innobase/xtrabackup/src $PWD/pxb/sources/pxb24/mysql-test
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
