from dataclasses import dataclass
import subprocess, os
from typing import List, Set, Dict, Optional
import argparse

build_dir = "/app/search.marginalia.nu/build"
docker_dir = "/app/search.marginalia.nu/docker"

@dataclass
class ServiceConfig:
    """Configuration for a service"""
    gradle_target: str
    docker_name: str
    instances: int | None
    deploy_tier: int
    groups: Set[str]

@dataclass
class DeploymentPlan:
    services_to_build: List[str]
    instances_to_deploy: Set[str]

@dataclass
class DockerContainer:
    name: str
    partition: int
    config: ServiceConfig

    def docker_name(self) -> str:
        if self.partition < 1:
            return f"{self.name}"
        return f"{self.name}-{self.partition}"

    def deploy_key(self) -> str:
        return f"{self.config.deploy_tier}.{self.partition}"

class BuildError(Exception):
    """Raised when a build fails"""
    def __init__(self, service: str, return_code: int):
        self.service = service
        self.return_code = return_code
        super().__init__(f"Build failed for {service} with code {return_code}")

def get_deployment_tag() -> str | None:
    """Get the deployment tag from the current HEAD commit, if one exists."""
    cmd = ['git', 'for-each-ref', '--points-at', 'HEAD', 'refs/tags', '--format=%(refname:short) %(subject)']
    result = subprocess.run(cmd, capture_output=True, text=True)

    if result.returncode != 0:
        raise RuntimeError(f"Git command failed: {result.stderr}")

    for tag in result.stdout.splitlines():
        if tag.startswith('deploy-'):
            return tag.split(' ')[1:]

    return None

def parse_deployment_tags(
    tag_messages: List[str],
    service_config: Dict[str, ServiceConfig]
) -> DeploymentPlan:
    """
    Parse deployment and hold tags using service configuration.

    Args:
        tag_messages: List of tag messages (e.g. ['deploy:all,-frontend', 'hold:index-service-7'])
        service_config: Dictionary mapping service names to their configuration

    Returns:
        DeploymentPlan containing services to build and instances to hold
    """
    services_to_build = set()
    services_to_exclude = set()
    instances_to_hold = set()

    available_services = set(service_config.keys())
    available_groups = set()

    partitions = set()

    for service in service_config.values():
        available_groups = available_groups | service.groups

    for tag in [tag.strip() for tag in tag_messages]:
        if tag.startswith('partition:'):
            for p in tag[10:].strip().split(','):
                partitions.add(int(p))
        if tag.startswith('deploy:'):
            parts = tag[7:].strip().split(',')

            for part in parts:
                part = part.strip()

                if part.startswith('-'):
                    service = part[1:]
                    if not service in available_services:
                        raise ValueError(f"Unknown service {service}")

                    services_to_exclude.add(service)
                elif part.startswith('+'):
                    service = part[1:]
                    if not service in available_services:
                        raise ValueError(f"Unknown service {service}")

                    services_to_build.add(service)
                else:
                    group = part
                    if not group in available_groups:
                        raise ValueError(f"Unknown service group {group}")
                    for name, service in service_config.items():
                        if group in service.groups:
                            services_to_build.add(name)

        elif tag.startswith('hold:'):
            instances = tag[5:].strip().split(',')
            instances_to_hold.update(i.strip() for i in instances if i.strip())

    print(partitions)

    # Remove any explicitly excluded services
    services_to_build = services_to_build - services_to_exclude

    # Validate that all specified services exist
    invalid_services = (services_to_build | services_to_exclude) - available_services
    if invalid_services:
        raise ValueError(f"Unknown services specified: {invalid_services}")

    to_deploy = list()
    for service in services_to_build:
        config = service_config[service]

        if config.instances == None:
            if config.docker_name in instances_to_hold:
                continue
            container = DockerContainer(config.docker_name, 0, config)

            if len(partitions) == 0 or 0 in partitions:
                to_deploy.append(container)
        else:
            for instance in range(1,config.instances + 1):
                if config.docker_name in instances_to_hold:
                    continue

                container_name = f"{config.docker_name}-{instance}"
                if container_name in instances_to_hold:
                    continue

                if len(partitions) == 0 or instance in partitions:
                    to_deploy.append(DockerContainer(container_name, instance, config))

    return DeploymentPlan(
        services_to_build=sorted(list(services_to_build)),
        instances_to_deploy=sorted(to_deploy, key = lambda c : c.deploy_key())
    )


def deploy_container(container: DockerContainer) -> None:

    """
    Run a docker deployment for the specified service and target.
    Raises BuildError if the build fails.
    """
    print(f"Deploying {container.name}")
    process = subprocess.Popen(
        ['docker', 'compose', '--progress', 'quiet', 'up', '-d', container.name],
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True
    )


    # Stream output in real-time
    while True:
        output = process.stdout.readline()
        if output == '' and process.poll() is not None:
            break
        if output:
            print(output.rstrip())

    return_code = process.poll()
    if return_code != 0:
        raise BuildError(container, return_code)

def deploy_services(containers: List[str]) -> None:
    print(f"Deploying {containers}")
    os.chdir(docker_dir)
    for container in containers:
        deploy_container(container)

def build_and_deploy(plan: DeploymentPlan, service_config: Dict[str, ServiceConfig]):
    """Execute the deployment plan"""
    run_gradle_build([service_config[service].gradle_target for service in plan.services_to_build])

    deploy_services(plan.instances_to_deploy)



def run_gradle_build(targets: str) -> None:
    """
    Run a Gradle build for the specified target.
    Raises BuildError if the build fails.
    """
    print(f"\nBuilding targets {targets}")
    process = subprocess.Popen(
        ['./gradlew', '-q'] + targets,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True
    )

    # Stream output in real-time
    while True:
        output = process.stdout.readline()
        if output == '' and process.poll() is not None:
            break
        if output:
            print(output.rstrip())

    return_code = process.poll()
    if return_code != 0:
        raise BuildError(service, return_code)

# Example usage:
if __name__ == '__main__':
    # Define service configuration
    SERVICE_CONFIG = {
        'search': ServiceConfig(
            gradle_target=':code:services-application:search-service:docker',
            docker_name='search-service',
            instances=2,
            deploy_tier=2,
            groups={"all", "frontend", "core"}
        ),
        'api': ServiceConfig(
            gradle_target=':code:services-application:api-service:docker',
            docker_name='api-service',
            instances=2,
            deploy_tier=1,
            groups={"all", "core"}
        ),
        'assistant': ServiceConfig(
            gradle_target=':code:services-core:assistant-service:docker',
            docker_name='assistant-service',
            instances=2,
            deploy_tier=2,
            groups={"all", "core"}
        ),
        'explorer': ServiceConfig(
            gradle_target=':code:services-application:explorer-service:docker',
            docker_name='explorer-service',
            instances=None,
            deploy_tier=1,
            groups={"all", "extra"}
        ),
        'dating': ServiceConfig(
            gradle_target=':code:services-application:dating-service:docker',
            docker_name='dating-service',
            instances=None,
            deploy_tier=1,
            groups={"all", "extra"}
        ),
        'index': ServiceConfig(
            gradle_target=':code:services-core:index-service:docker',
            docker_name='index-service',
            instances=10,
            deploy_tier=3,
            groups={"all", "index"}
        ),
        'executor': ServiceConfig(
            gradle_target=':code:services-core:executor-service:docker',
            docker_name='executor-service',
            instances=10,
            deploy_tier=3,
            groups={"all", "executor"}
        ),
        'control': ServiceConfig(
            gradle_target=':code:services-core:control-service:docker',
            docker_name='control-service',
            instances=None,
            deploy_tier=0,
            groups={"all", "core"}
        ),
        'query': ServiceConfig(
            gradle_target=':code:services-core:query-service:docker',
            docker_name='query-service',
            instances=2,
            deploy_tier=2,
            groups={"all", "query"}
        ),
    }

    try:
        parser = argparse.ArgumentParser(
            prog='deployment.py',
            description='Continuous Deployment helper')
        parser.add_argument('-v', '--verify', help='Verify the tags are valid, if present', action='store_true')
        parser.add_argument('-t', '--tag', help='Use the specified tag value instead of the head git tag starting with deploy-')

        args = parser.parse_args()
        tags = args.tag
        if tags is None:
            tags = get_deployment_tag()
        else:
            tags = tags.split(' ')



        if tags != None:
            print("Found deployment tags:", tags)

            plan = parse_deployment_tags(tags, SERVICE_CONFIG)

            print("\nDeployment Plan:")
            print("Services to build:", plan.services_to_build)
            print("Instances to deploy:", [container.name for container in plan.instances_to_deploy])

            if not args.verify:
                print("\nExecution Plan:")

                build_and_deploy(plan, SERVICE_CONFIG)
        else:
            print("No tags found")

    except ValueError as e:
        print(f"Error: {e}")
