#!/usr/bin/env python3

import os
import re
import shlex
import subprocess
import time


WD = os.path.join("/", "usr", "local", "src", "TaoStore")
CONFIGS_DIR = os.path.join(WD, "configs")
DEFAULT_CONFIG = os.path.join(CONFIGS_DIR, "default.config")
EXP_CONFIG = os.path.join(CONFIGS_DIR, "experiment.config")

BASE_CMD = "java --class-path ./out/production/TaoStore:./libs/guava-19.0.jar:./libs/commons-math3-3.6.1.jar:./libs/junit-4.11.jar"
CLIENT_CLASS = "TaoClient.TaoClient"
PROXY_CLASS = "TaoProxy.TaoProxy"
SERVER_CLASS = "TaoServer.TaoServer"


def run_sync(cmd):
    subprocess.run(shlex.split(cmd),
                   cwd=WD,
                   check=True)

def run(cmd):
    return subprocess.Popen(shlex.split(cmd),
                            cwd=WD,
                            stderr=subprocess.STDOUT)


def client_cmd(config):
    cmd = "{} {} --config_file {}".format(BASE_CMD, CLIENT_CLASS, config["config_file"])
    cmd += " --runType load_test --load_test_type asynchronous"
    cmd += " --load_size {}".format(config["num_operations"])
    cmd += " --data_set_size {}".format(config["num_blocks"])
    return cmd


def proxy_cmd(config):
    cmd = "{} {} --config_file {}".format(BASE_CMD, PROXY_CLASS, config["config_file"])
    return cmd


def server_cmd(config):
    cmd = "{} {} --config_file {}".format(BASE_CMD, SERVER_CLASS, config["config_file"])
    return cmd


def generate_configs():
    configs = []
    default = {
        "config_file": DEFAULT_CONFIG,
        "num_blocks": 1000,
        "num_operations": 1000,
    }

    # SCALABILITY
    for num_clients in [2**i for i in range(0, 4)]:
        scalability = default.copy()
        scalability["num_clients"] = num_clients

        configs.append(scalability)

    
    return configs


def replace_config_line(config_string, key, value):
    return re.sub("{}=.*$".format(key),
                  "{}={}\n".format(key, value),
                  config_string,
                  flags=re.MULTILINE)


def write_config_file(config):
    with open(config["config_file"], "r") as config_file:
        config_string = config_file.read()

    config_string = replace_config_line(config_string,
                                        "proxy_thread_count", config["num_clients"])


    with open(EXP_CONFIG, "w") as config_file:
        config_file.write(config_string)
    
    config["config_file"] = EXP_CONFIG


def reset_state():
    run_sync("rm -f oram.txt")
    run_sync("ant clean all")


def run_exp(config):
    reset_state()
    write_config_file(config)

    server = run(server_cmd(config))
    proxy = run(proxy_cmd(config))
    client = run(client_cmd(config))

    client.wait()

    server.kill()
    proxy.kill()


def run_all():
    configs = generate_configs()

    for config in configs:
        run_exp(config)


def main():
    run_all()


if __name__ == "__main__":
    main()


