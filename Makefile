TITLE = Assignment1

main: output.dat

%.class: %.java
	javac -d . $<

clean:
	-rm -fr *.class *.dat

output.dat: ${TITLE}.class
	java ${TITLE} $<

testcase: output.dat
	sort $< > $@

test: output.dat
	sort $< | diff testcase /dev/stdin

.PHONY: main clean test
