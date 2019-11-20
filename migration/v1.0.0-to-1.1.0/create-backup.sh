#!/bin/sh
# Define migration functions
#!/bin/sh
# Define migration functions
ES=${1:-http://localhost:9200}
# For testing prefix 'echo ' 
RESTCLIENT="curl -K curl.config"

for index in backup/*.json; do
   echo "Save index $index to ${index}_1.0.0_"
   echo "-------------------------------"
   $RESTCLIENT --request POST --url ${ES}/_reindex?wait_for_completion=true --data @${index}
   echo
   echo "-------------------------------" 
done
