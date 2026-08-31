# DevBrowser — offline build entry points
.PHONY: all apk icons verify clean info

all: apk

apk:
	bash tools/build.sh

icons:
	python3 tools/make_icons.py

verify: apk

info:
	bash tools/apk-info.sh

clean:
	rm -rf app/build
