

PNGS+=$(shell find * -type f -name "*.png")
NACS+=$(PNGS:%.png=%.nac)

.PHONY: all

all: $(NACS)

%.nac: %.png
	./CompileMap.sh $@

