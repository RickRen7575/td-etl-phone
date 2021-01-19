import java.time.LocalDate
import java.time.format.DateTimeFormatter

pipeline {
    agent any
    tools { 
        maven 'Maven 3.6.3' 
        jdk 'jdk8' 
    }
    parameters { 
        choice(name: 'PROFILE', choices: ['dev', 'qa', 'prod'], description: 'This determines whether this is a run job or a build job')
        choice(name: 'TYPE', choices: ['RUN', 'BUILD'], description: 'This determines whether this is a run job or a build job')
        string(name: 'ADDITIONAL_PARAMS', defaultValue: 'NULL', description: 'Additional parameters to add to the execution of the ETL jobs.') 
    }
    environment {
        NEXUS_API = 'https://nexus.tirediscountersdirect.com/nexus/service/rest/v1'
    }
    stages {

        stage('Build') {
            when {
                expression { params.TYPE == 'BUILD' }
            }
            stages {
                stage ('Build') {
                    steps {
                        sh "mvn clean package install -DskipTests=true -P ${PROFILE}"
                    }
                }

                stage ('Deploy') {
                    steps {
                        sh "mvn deploy -P ${PROFILE}"
                    }
                }
            }
        }

        stage('Run') {
            when {
                expression { params.TYPE == 'RUN' }
            }
            environment { 
                ARTIFACT = 'td-etl-phone'
                VERSION = '0.1.0'
            }
            steps {
                configFileProvider(
                        [configFile(fileId: '79d7480d-2378-4fe9-a767-b566fc6d90f8', variable: 'ENCRYPTION_KEY_FILE')]) {

                    sh "curl -X GET --header 'Accept: application/json' '${NEXUS_API}/search/assets/download?sort=version&repository=${getMavenRepo(params.PROFILE)}&maven.groupId=com.tirediscounters&maven.artifactId=${ARTIFACT}&maven.baseVersion=${addPackageSuffix(VERSION, params.PROFILE)}&maven.extension=jar' -L -o ${ARTIFACT}.jar"
                    sh "java -jar ${ARTIFACT}.jar --spring.profiles.active=${PROFILE} --key.file=${env.ENCRYPTION_KEY_FILE} --secretkey.file.path=${env.ENCRYPTION_KEY_FILE} ${addAdditionalParams(params.ADDITIONAL_PARAMS)}"
                }
            }
        }
    }
}

def getProfileFromBranch(branch) {
    return branch == 'origin/master' ? 'prod' : 'dev'
}

def getMavenRepo(String profile) {
    return ['qa', 'prod'].contains(profile)  ? 'maven-releases' : 'maven-snapshots'
}

def getPackageSuffix(Boolean isProd) {
    return isProd ? '' : '-SNAPSHOT'
}

def addPackageSuffix(String version, String profile) {
    return version + getPackageSuffix(['qa', 'prod'].contains(profile))
}

def addAdditionalParams(String additionalParams) {
    additionalParams = additionalParams != 'NULL' ? additionalParams : ' '
    return additionalParams + ' '
}

def isWorkHours() {
    def time = new Date().format('HH:mm:ss')
    def isDuringTheDay = time >= '06:00:00' && time <= '21:00:00'
    echo "This job is running at ${time}. During work hours: ${isDuringTheDay}"
    return isDuringTheDay
}