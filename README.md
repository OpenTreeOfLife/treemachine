opentree-treemachine
===============
Installation
---------------
treemachine is managed by Maven v. 2 (including the dependencies). In order to compile and build treemachine, it is easiest to let Maven v. 2 do the hard work.

On Ubuntu you can install Maven v. 2 with:
sudo apt-get install maven2

Once Maven v. 2 is installed, you can 
	
	git clone git@github.com:OpenTreeOfLife/opentree-treemachine.git

then 
	
	mvn clean compile assembly:single

