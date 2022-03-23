# start custom jenkins image (plugins and some settings are already in stored in mounted docker volume)
# https://www.jenkins.io/doc/book/installing/docker/
# https://blog.container-solutions.com/running-docker-in-jenkins-in-docker
docker build -t jenkins-docker .

docker run -p 8080:8080 \
    -p 50000:50000 \
    -v /var/run/docker.sock:/var/run/docker.sock \
    -v jenkins_home:/var/jenkins_home \
    -v /Users/samavasi/.kube/config4docker:/var/jenkins_home/.kube/config \
    --network=kind \
    -d jenkins-docker
