SRC_DIR = code

all: compile

compile:
	javac $(SRC_DIR)/*.java

run: compile
	java -cp $(SRC_DIR) Warehouse

clean:
	rm -f $(SRC_DIR)/*.class
