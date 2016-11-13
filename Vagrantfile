# -*- mode: ruby -*-
# vi: set ft=ruby :
Vagrant.configure("2") do |config|
  config.vm.box = "hashicorp/precise64"

  config.vm.provider "virtualbox" do |vb|
    # Display the VirtualBox GUI when booting the machine
    vb.gui = false

    # Customize the amount of memory on the VM:
    vb.memory = "8024"
  end

  config.vm.provision "shell", inline: <<-SHELL
    sudo add-apt-repository ppa:openjdk-r/ppa
    sudo apt-get install -y openjdk-7-jdk
    sudo apt-get update
    sudo apt-get upgrade -y
    wget https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein
    chmod +x lein
    sudo mv lein /usr/local/bin
    #}cd /vagrant
    #lein install
  SHELL
end
