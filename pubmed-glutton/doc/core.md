CORE API

* batch coreID 
/articles/get	batch	1 requests per 10 seconds

https://core.ac.uk:443/api-v2/articles/get?metadata=true&fulltext=false&citations=false&similar=false&duplicate=true&urls=true&faithfulMetadata=true&apiKey=xv8wpum0jYCkZrH7N4iOTMa6L5WD1GRE

body: JSON array with CORE IDs of articles that need to be fetched
[29429717, 8388736, 2097197]

* PDF
/articles/get/{coreId}/download/pdf	
single 10 requests per 10 seconds

* repositoryID

https://core.ac.uk:443/api-v2/repositories/get/645?apiKey=xv8wpum0jYCkZrH7N4iOTMa6L5WD1GRE

https://core.ac.uk:443/api-v2/repositories/get?apiKey=xv8wpum0jYCkZrH7N4iOTMa6L5WD1GRE
[1,2,3,4,5] 
max 100

* batch repositoryID

https://core.ac.uk:443/api-v2/repositories/get?apiKey=xv8wpum0jYCkZrH7N4iOTMa6L5WD1GRE

body: JSON array with CORE repository IDs of repositories that need to be fetched
[1,2,3]
