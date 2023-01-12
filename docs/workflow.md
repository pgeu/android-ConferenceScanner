# Basic workflow


## Registration

To "connect" the app with a conference, a registration link is
clicked. This can either be a check-in or a sponsor badge scanning
link. They follow the patterns:

Check-in links
:  https://site/events/eventname/checkin/*token*/

Badge scanning links
:  https://site/events/sponsor/scanning/*token*/

Where the token is an opaque string of hex digits that is unique for
each attendee.

Depending on the pattern, a different suffix (yes, really) is used to
get the status. They will be:

Check-in links
:  https://site/events/eventname/checkin/*token*/api/status/

Badge scanning links
:  https://site/events/sponsor/scanning/*token*/api/?status=1

In each case a single JSON object is returned, that has some
properties that are the same:

```
{
    "confname": "PGConf.EU 2019",
    "name": "Magnus Hagander",
    "scanner": "mha"
}

```

When it comes to a check-in scan, this object will contain all this
data, except the field `scanner` will be called `user` and *also* some
more data, such as:

```
{
    "active": false,
    "admin": true,
    "confname": "PGConf.EU 2019",
    "name": "Magnus Hagander",
    "user": "mha"
}
```

The fields indicate:

* confname

   The name of the conference, that should be used to reference it in
   the app.

* name

   The name of the person doing the scanning (will always be a
   registered user)

* scanner / user

   The username of the person doing the scanning

* active

   If check-in is open for this conference. (Scanning is always open)

* admin

   If the user scanning is an administrator. Only administrators will
   have permissions to view statistics of the scanning.


## Check-in flow

When checking in a user, the normal path is to turn on the camera and
scan the barcode on the users *ticket*. The barcode should return a
string of the format `https://<site>/t/id/*token*/` or `ID$*token*$ID`.
There is also a special test  code that has the contents
`TESTTESTTESTTEST` that can be used  to validate the test.

Once the code has been scanned, a http GET is made to
`*baseurl*/api/lookup/?lookup=*barcode*`, which returns information
about the attendee:

```
{
    "reg": {
        "additional": [
            "Additional Option 1",
            "Some more training"
        ],
        "checkedin": {
            "at": "2019-12-27T16:09:14.815",
            "by": "Magnus Hagander"
        },
        "company": "MegaCorp Inc",
        "id": 123,
        "name": "Magnus Hagander",
        "partition": "H",
        "photoconsent": "Photos NOT OK",
        "tshirt": null,
        "type": "Normal"
    }
}
```

The lookup API endpoint will return http status `412` if checkin is
not open or 404 if the token is not found. The return will always be
an object with a single sub-object in it called `reg`. The fields in
this object are:

* name
   Full name of the attendee

* company
   Company name of the attendee

* type
   Registration type of the attendee

* id
   Internal ID number (primary key) of the attendee

* tshirt
   T-Shirt size picked at registration, if any

* partition
   Queue partition to use, if any

* photoconsent
   String about photo consent (yes or no), if any

* additional
   An array with a list of additional options for this attendee

* checkedin
   *If* the attendee has already been checked in, information about
   the checkin. Property does not exist if the user is not already
   checked in.


The information in this dialog should be presented to the scanner, who
can then choose to check the user in, or cancel (obviously cannot
choose to check the user in if already checked in).

To actually check the user in, make a http POST to
`*baseurl*/api/checkin/`, with a regular form parameter named `id` set
to the id value returned in the lookup.
This endpoing will return `412` if checkin is closed or if the user is
already checked in. If the checkin is completed, it returns the same
data as the call to `lookup`.

This data is then shown to the scanner again, since it's very common
to need to lookup this information right away (such as t-shirt size or
which training sessions are booked).

### Checkin-via-search flow

The check-in flow can also be triggered via search, in case the
attendee does not have a ticket with a barcode. In this case the
scanner is prompted for a string to search for. This is then passed in
by a call to `*baseurl*/api/search/?search=term`. The search will
return a json object with a single key, `regs`, which in turn contains
an array of matching registrations. For each registrations, the
contents are identical to the `lookup` call.

The user is presented a list of matching registrations, and once they
have picked one, the workflow follows the same as the scan-by-barcode
one.


## Sponsor scanner flow

The sponsor scanner flow is similar to the check-in flow, but the API
endpoints are different, and the barcodes instead use the format
`AT$*token*$AT`. There is a separate barcode for testing badge
scanning, which is `AT$TESTTESTTESTTEST$AT`.

To scan the code, the request is made to
`*baseurl*/api/?token=*barcode*.

If the attendee is not found, `404` is returned. If the attendee is
found, a json object representing the attendee will be returned:

```
{
    "company": "Secret Corp",
    "country": "Sweden",
    "email": "test@example.com",
    "name": "Magnus Hagander",
    "note": "Some Special Note "
}
```

The fields are self-describing, except possibly for `note`. This field
contains a *per-scanner* note for the attendee. That means that once
the lookup has succeeded, a dialog is shown to the scanner allowing
them to view the data and add this note. If the same badge is scanned
more than once, this note can be *edited', so the dialog is shown with
the default value being the previous note.

To store the scan, a POST call is made to `*baseurl*/api/`, with the
post variable `token` set to the barcode, and the post variable `note`
set to the note. Newlines may be included in the note.
