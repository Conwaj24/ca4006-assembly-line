TITLE = Assignment1

main: output.dat

%.class: %.java
	javac -d . $<

clean:
	-rm -fr *.class *.dat

output.dat: ${TITLE}.class
	java ${TITLE} $< | tee $@

testcase: output.dat
	awk '{$$1="";print $$0}' $< | sort > $@

test: output.dat
	awk '{$$1="";print $$0}' $< | sort | diff testcase /dev/stdin

.PHONY: main clean test
