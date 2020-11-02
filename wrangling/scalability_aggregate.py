#!/usr/bin/env python3

import argparse
import csv
import os
import re


def parse_args():
    parser = argparse.ArgumentParser(description="Aggregate scalability results")
    parser.add_argument("-i", "--input-log-dir", type=str, required=True,
                        help="input log directory")

    parser.add_argument("-o", "--output-file", type=str, required=True,
                        help="output file name")

    return parser.parse_args()


def parse_headers(dir_name):
    headers = []

    for s in dir_name.split("__"):
        parts = s.split("@")
        headers.append(parts[0])

    return headers


def parse_values(dir_name):
    values = []

    for s in dir_name.split("__"):
        parts = s.split("@")
        values.append(parts[1])

    return values


def aggregate_measurements(log_dir):
    headers = []
    rows = []
    with os.scandir(log_dir) as entries:
        for entry in entries:
            dir_name = entry.name
            if not dir_name.startswith('.') and entry.is_dir():
                if len(headers) == 0:
                    headers = parse_headers(dir_name) + ["throughput"]

                fname = os.path.join(log_dir, dir_name, "client.log")

                with open(fname, "r") as f:
                    match = re.search(r"^.*Throughput\:\s([0-9\.]+)", f.read(), re.MULTILINE)
                    tput = match.group(1)
                    rows.append(parse_values(dir_name) + [tput])


        return headers, rows


def write_measurements(fname, headers, rows):
    with open(fname, "w+") as f:
        writer = csv.writer(f)
        writer.writerow(headers)

        for row in rows:
            writer.writerow(row)


def main():
    args = parse_args()

    headers, rows = aggregate_measurements(args.input_log_dir)
    write_measurements(args.output_file, headers, rows)


if __name__ == "__main__":
    main()
