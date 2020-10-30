#!/usr/bin/env python3

import os
import shlex
import subprocess
import time


WD = os.path.join("/", "usr", "local", "src", "TaoStore")
CONFIGS_DIR = os.path.join(WD, "configs")
DEFAULT_CONFIG = os.path.join(CONFIGS_DIR, "config.properties")

BASE_CMD = "java --class-path ./out/production/TaoStore:./libs/guava-19.0.jar:./libs/commons-math3-3.6.1.jar:./libs/junit-4.11.jar"
CLIENT_CLASS = "TaoClient.TaoClient"
PROXY_CLASS = "TaoProxy.TaoProxy"
SERVER_CLASS = "TaoServer.TaoServer"


def run(cmd):
    return subprocess.Popen(shlex.split(cmd),
                            cwd=WD,
                            stderr=subprocess.STDOUT)


def client_cmd(config=DEFAULT_CONFIG, args=""):
    return "{} {} --config_file {} {}".format(BASE_CMD, CLIENT_CLASS, config, args)


def proxy_cmd(config=DEFAULT_CONFIG, args=""):
    return "{} {} --config_file {} {}".format(BASE_CMD, PROXY_CLASS, config, args)


def server_cmd(config=DEFAULT_CONFIG, args=""):
    return "{} {} --config_file {} {}".format(BASE_CMD, SERVER_CLASS, config, args)


def generate_configs():
    return []


def write_config_file(config):
    pass


def reset_state():
    pass


def run_exp(config):
    reset_state()
    write_config_file(config)

    server = run(server_cmd())
    proxy = run(proxy_cmd())
    client = run(client_cmd(args="--runType load_test --load_test_type asynchronous --load_size 1000 --data_set_size 1000"))

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


