Artifactory Property Sets Config User Plugin
============================================

Allows REST access to the property set configuration. This plugin exposes five
executions:

getPropertySetsList
-------------------

`getPropertySetsList` returns a JSON list containing the names of all the
currently configured property sets.

For example:

```
$ curl -u admin:password 'http://localhost:8081/artifactory/api/plugins/execute/getPropertySetsList'
[
    "artifactory",
    "newpropset"
]
```

getPropertySet
--------------

`getPropertySet` returns a JSON representation of the current configuration of a
given property set. The name of the requested property set is passed as the
parameter `name`. The returned JSON string has the following fields:

- `name`: The property set's unique ID name.
- `properties`: A list of the properties in this set.

Each property in the property set has the following fields:

- `name`: The property's unique ID name.
- `propertyType`: The type of property. Values can be `"ANY_VALUE"`,
  `"SINGLE_SELECT"`, or `"MULTI_SELECT"`.
- `predefinedValues`: A list of values that are predefined for this property.

Each predefined value in the property has the following fields:

- `value`: The value itself.
- `defaultValue`: Whether the value is a default value.

For example:

```
$ curl -u admin:password 'http://localhost:8081/artifactory/api/plugins/execute/getPropertySet?params=name=newpropset'
{
    "name": "newpropset",
    "properties": [
        {
            "name": "newprop1",
            "predefinedValues": [

            ],
            "propertyType": "ANY_VALUE"
        },
        {
            "name": "newprop2",
            "predefinedValues": [
                {
                    "value": "yes",
                    "defaultValue": true
                },
                {
                    "value": "no",
                    "defaultValue": false
                }
            ],
            "propertyType": "SINGLE_SELECT"
        }
    ]
}
```

deletePropertySet
-----------------

`deletePropertySet` deletes a property set from the Artifactory instance. The
name of the property set to delete is passed as the parameter `name`.

For example:

```
$ curl -u admin:password -X DELETE 'http://localhost:8081/artifactory/api/plugins/execute/deletePropertySet?params=name=newpropset'
```

addPropertySet
--------------

`addPropertySet` adds a new property set to the Artifactory instance. The
property set to add is defined by a JSON object sent in the request body, with
the same schema used by `getPropertySet`.

For example:

```
$ curl -u admin:password -X POST -H 'Content-Type: application/json' -d '{
> "name": "newpropset",
> "properties": [{
> "name": "newprop1",
> "predefinedValues": [],
> "propertyType": "ANY_VALUE"
> }, {
> "name": "newprop2",
> "propertyType": "SINGLE_SELECT",
> "predefinedValues": [{
> "value": "yes", "defaultValue": true
> }, {
> "value": "no", "defaultValue": false
> }]}]
> }' 'http://localhost:8081/artifactory/api/plugins/execute/addPropertySet'
```

updatePropertySet
-----------------

`updatePropertySet` updates an existing property set. The name of the property
set to modify is passed as the parameter `name`. The modifications are defined
by a JSON object sent in the request body, with the same schema used by
`getPropertySet` and `addPropertySet`. Only the fields that should be modified
need to be included in the JSON representation, and all other fields will be
preserved.

For example:

```
$ curl -u admin:password -X POST -H 'Content-Type: application/json' -d '{
> "properties": [{
> "name": "newprop1",
> "predefinedValues": [],
> "propertyType": "ANY_VALUE"
> }]
> }' 'http://localhost:8081/artifactory/api/plugins/execute/updatePropertySet?params=name=newpropset'
```
