'''
    Merge csv record with json export based on DOI

    Usage:
    python3 merge_records.py --csv records.csv --json records.json --output combined_records.csv

'''

import sys
import os
import io
import json
import time
import csv
from tqdm import tqdm
import argparse

def main(csv_path, json_path, output_path):
    if csv_path is None or json_path is None:
        print('the path to resource files to process has not been provided')
        sys.exit()

    # load json file
    with open(json_path) as json_file:
        json_data = json.load(json_file)

    # read csv
    nb_match = 0
    nb_records = 0
    nb_record_with_doi = 0
    nb_author_countries = 0
    nb_funders = 0
    nb_affiliations = 0
    with open(csv_path) as csvfile:
        with open(output_path, mode='w') as csvoutput:
            record_reader = csv.reader(csvfile)
            record_writer = csv.writer(csvoutput, delimiter=',', quotechar='"', quoting=csv.QUOTE_MINIMAL)

            for record in record_reader:
                # header is the same
                if nb_records == 0:
                    record_writer.writerow(record)
                    nb_records += 1
                    continue

                nb_records += 1
                #print(', '.join(record))
                doi = record[1].lower()
                if doi is None or len(doi) == 0:
                    record_writer.writerow(record)
                    continue
                nb_record_with_doi += 1
                json_entry = _find_json_entry(doi, json_data)
                #print(json_entry)
                if json_entry is not None:
                    nb_match += 1

                    # enrich csv row to be outputed 
                    affiliation = ""
                    all_affiliations = []
                    grid = ""
                    all_grids = []
                    author_countries = ""
                    all_countries = []
                    if "authors" in json_entry:
                        for author in json_entry["authors"]:
                            local_affiliation = ""
                            if "affiliations" in author:
                                affiliations = author["affiliations"]
                                for affiliation_info in affiliations:
                                    if "name" in affiliation_info:
                                        local_affiliation = affiliation_info["name"]
                                        if local_affiliation.endswith("."):
                                            local_affiliation = local_affiliation[0:len(local_affiliation)-1]
                                        if local_affiliation not in all_affiliations:
                                            all_affiliations.append(local_affiliation)
                                    if "grid" in affiliation_info and affiliation_info["grid"] is not None:
                                        grid_info = affiliation_info["grid"]
                                        if "id" in grid_info:
                                            if grid_info["id"] not in all_grids:
                                                all_grids.append(grid_info["id"])
                                        if "addresses" in grid_info:
                                            if grid_info["addresses"]:
                                                for address_info in grid_info["addresses"]:  
                                                    if "country_code" in address_info:  
                                                        if address_info["country_code"] not in all_countries:
                                                            all_countries.append(address_info["country_code"])

                        if len(all_affiliations)>0:
                            for the_affiliation in all_affiliations:
                                affiliation += ";"+the_affiliation
                        if len(all_grids)>0:
                            for the_grid in all_grids:
                                grid+=";"+the_grid

                        if len(all_countries)>0:
                            for the_country in all_countries:
                                author_countries += ";"+the_country

                    if grid.startswith(";"):
                        grid = grid[1:]
                    if affiliation.startswith(";"):
                        affiliation = affiliation[1:]    
                    if author_countries.startswith(";"):
                        author_countries = author_countries[1:]

                    if author_countries is not None and len(author_countries)>0:
                        nb_author_countries += 1
                    
                    funding_organizations = ""
                    all_funding_organizations = []
                    funding_countries = ""
                    all_funding_countries = []
                    if "funding" in json_entry:
                        for the_funding in json_entry["funding"]:
                            if "org" in the_funding:
                                if the_funding["org"] not in all_funding_organizations:
                                    all_funding_organizations.append(the_funding["org"])
                            if "country" in the_funding and the_funding["country"] is not None:
                                if the_funding["country"] not in all_funding_countries:
                                    all_funding_countries.append(the_funding["country"])
                    if len(all_funding_organizations)>0:
                        for the_org in all_funding_organizations:
                            funding_organizations += ";"+the_org
                    if funding_organizations.startswith(";"):
                        funding_organizations = funding_organizations[1:]
                    if len(all_funding_countries)>0:    
                        for the_country in all_funding_countries:
                            funding_countries += ";"+the_country
                    if funding_countries.startswith(";"):
                        funding_countries = funding_countries[1:]

                    if funding_organizations is not None and len(funding_organizations)>0:
                        nb_funders += 1

                    # affiliation
                    if record[len(record)-5] is None or len(record[len(record)-5])==0:
                        record[len(record)-5] = affiliation
                        nb_affiliations += 1

                    # author_countries
                    if record[len(record)-4] is None or len(record[len(record)-4])==0:
                        record[len(record)-4] = author_countries

                    # grid
                    if record[len(record)-3] is None or len(record[len(record)-3])==0: 
                        record[len(record)-3] = grid

                    # funding organization
                    if record[len(record)-2] is None or len(record[len(record)-2])==0:
                        record[len(record)-2] = funding_organizations

                    # funding_country
                    if record[len(record)-1] is None or len(record[len(record)-1])==0:
                        record[len(record)-1] = funding_countries

                    # in case abstract is missing but present in the json complementary data
                    if record[4] is None or len(record[4]) == 0:
                        if "abstract" in json_entry and len(json_entry["abstract"]) > 0:
                            record[4] = json_entry["abstract"]

                record_writer.writerow(record)

        print('total records:', nb_records)
        print('total records with doi:', nb_record_with_doi)
        print('number of found records:', nb_match)
        print('number of author countries added:', nb_author_countries)
        print('number of affiliation field added:', nb_affiliations)
        print('number of funding added:', nb_funders)

def _find_json_entry(doi, json_data):
    for entry in json_data: 
        the_ids = entry['external_ids']   
        for the_id in the_ids:
            the_type = the_id['type']
            if the_type == 'doi':
                local_doi = the_id['value']
                if doi == local_doi.lower():
                    return entry
    return None

if __name__ == "__main__":

    parser = argparse.ArgumentParser(description = "Example of information extraction from GROBID XML/TEI result file")
    parser.add_argument("--csv", help="path of the csv file to be processed")
    parser.add_argument("--json", help="path of the json file to enrich data")
    parser.add_argument("--output", default="./out.csv", help="csv file for the aggregated information") 

    args = parser.parse_args()

    csv_path = args.csv
    json_path = args.json
    output_path = args.output

    if csv_path is None:
        print('the path to the csv file to process has not been indicated')
    elif json_path is None:
        print('the path to the json file to be used for aggregation has not been indicated')
    else:
        main(csv_path, json_path, output_path)

