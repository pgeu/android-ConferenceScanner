#!/bin/bash
set -e

for f in ic_launcher ic_launcher_round ; do
    convert $f.png -resize 48x48 ../app/src/main/res/drawable-mdpi/${f}.png
    convert $f.png -resize 72x72 ../app/src/main/res/drawable-hdpi/${f}.png
    convert $f.png -resize 96x96 ../app/src/main/res/drawable-xhdpi/${f}.png
    convert $f.png -resize 144x144 ../app/src/main/res/drawable-xxhdpi/${f}.png
    convert $f.png -resize 192x192 ../app/src/main/res/drawable-xxxhdpi/${f}.png
done

convert ic_launcher.png -resize 512x512 ../app/src/main/ic_launcher-web.png
