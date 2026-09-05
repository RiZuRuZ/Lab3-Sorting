javac -d bin src\Main.java src\alg\*.java
java -cp bin Main


CD your path

cd 'D:\#Java-Practice\##SS2\Lab3 Sorting'
& 'C:\Users\User\.maven\maven-3.10.0-rc-1\bin\mvn.cmd' clean compile
& 'C:\Users\User\.maven\maven-3.10.0-rc-1\bin\mvn.cmd' exec:java

gcc -std=c11 -O2 -DTIME_UNIT=TIME_UNIT_NS `
  -o benchmark-ns.exe `
  main.c recorder.c sorters.c

.\benchmark-ns.exe


---------------------
gcc -std=c11 -O2 -DTIME_UNIT=TIME_UNIT_MS `
  -o benchmark-ms.exe `
  main.c recorder.c sorters.c

.\benchmark-ms.exe