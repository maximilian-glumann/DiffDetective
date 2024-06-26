#!/usr/bin/env bash

if [ "$1" == '' ] || [ "$1" == '--help' ] || [ "$1" == '-help' ]; then
  echo ">>>>>>>>> USAGE <<<<<<<<<<"
  echo "Either fully run DiffDetective as presented in the paper (replication), do quick setup verification (verification),
  or run DiffDetective on a custom dataset by providing the path to the dataset file."
  echo ""
  echo "-- Examples --"
  echo "Run replication: './execute.sh replication'"
  echo "Validate the setup: './execute.sh verification'"
  echo "# See ./docs/datasets/esecfse22-verification.md for format details"
  echo "Custom dataset: './execute.sh path/to/my_dataset.md'"
  exit
fi
cd /home/sherlock || exit
  cd holmes || exit

if [ "$1" == 'replication' ]; then
  echo "Running full replication. Depending on your system, this will require several hours or even a few days."
  java -cp DiffDetective.jar org.variantsync.diffdetective.experiments.views.Main docs/datasets/esecfse22-replication.md
elif [ "$1" == 'verification' ]; then
  echo "Running a short verification."
  java -cp DiffDetective.jar org.variantsync.diffdetective.experiments.views.Main docs/datasets/esecfse22-verification.md
else
  echo ""
  echo "Running detection on a custom dataset with the input file $1"
  echo ""
  java -cp DiffDetective.jar org.variantsync.diffdetective.experiments.views.Main "$1"
fi
echo "Collecting results."
cp -r results/* ../results/
cd ..
python3 analyze_data.py results/views/current
echo "The results are located in the 'results' directory."

