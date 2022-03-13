TITLE = Assignment1

#${TITLE}.class

%.class: %.java
	javac -d . $<

clean:
	-rm -fr *.class *.dat

test: ${TITLE}.class
	java ${TITLE} $<

.PHONY: clean test
