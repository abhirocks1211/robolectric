#!/usr/bin/env ruby
#
# This script mavenizes all dependencies from the Android SDK required to build Robolectric.
#
# Usage:
#   install-dependencies.rb
#
# Assumptions:
#  1. You've got one or more Android SDKs and Google APIs installed locally.
#  2. Your ANDROID_HOME environment variable points to the Android SDK install directory.
#  3. You have installed the Android Support Repository and Google Repository libraries from the SDK installer.
#
require 'pathname'
require 'tmpdir'

def run_cmd(*args)
  puts args
  system *args
end

class M2Repo
  def initialize(path)
    @path = path
  end

  def contains?(group_id, artifact_id, version)
    jar_path(group_id, artifact_id, version).exist?
  end

  def pom(group_id, artifact_id, version)
    <<-EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd" xmlns="http://maven.apache.org/POM/4.0.0"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <modelVersion>4.0.0</modelVersion>
  <groupId>#{group_id}</groupId>
  <artifactId>#{artifact_id}</artifactId>
  <version>#{version}</version>
  <description>POM was created from robolectric install-dependencies.rb</description>
</project>
EOF
  end

  def install(group_id, artifact_id, version, file)
    artifact_dir(group_id, artifact_id, version).mkpath
    pom_path(group_id, artifact_id, version).open("w") { |f| f << pom(group_id, artifact_id, version) }
    FileUtils.cp(file, jar_path(group_id, artifact_id, version))
    # run_cmd("mvn -q install:install-file -DgroupId='#{group_id}' -DartifactId='#{artifact_id}' -Dversion='#{version}' -Dfile='#{file}' -Dpackaging=jar")
  end

  def artifact_dir(group_id, artifact_id, version)
    @path + group_id.gsub(/\./, '/') + artifact_id + version
  end

  def pom_path(group_id, artifact_id, version)
    artifact_dir(group_id, artifact_id, version) + "#{artifact_id}-#{version}.pom"
  end

  def jar_path(group_id, artifact_id, version)
    artifact_dir(group_id, artifact_id, version) + "#{artifact_id}-#{version}.jar"
  end
end

class AndroidRepo
  def initialize(android_home, path, m2_repo)
    @android_home = android_home
    @path = android_home.base_dir + "extras/#{path}/m2repository"
    @m2_repo = m2_repo

    # Don't move further if we have an invalid repo root directory
    check_exists!
  end

  def check_exists!
    unless @path.exist?
      puts "Repository #{@path} not found!"
      puts "Make sure that the 'ANDROID_HOME' Environment Variable is properly set in your development environment pointing to your SDK installation directory."
      exit 1
    end
  end

  def install_jar(group_id, artifact_id, version, archive, &block)
    unless File.exist?(archive)
      puts "#{group_id}:#{artifact_id} not found!"
      puts "Make sure that the 'Android Support Repository' and 'Google Repository' is up to date in the SDK manager."
      exit 1
    end

    puts "Installing JAR #{group_id}:#{artifact_id}, version #{version} from \'#{archive}\'."
    @m2_repo.install(group_id, artifact_id, version, archive)
    block.call(dir) if block_given?
  end

  def install_aar(group_id, artifact_id, version, sdk_package, &block)
    archive = concat_maven_file_segments(group_id, artifact_id, version, "aar")

    puts "Installing AAR #{group_id}:#{artifact_id}, version #{version} from \'#{archive}\'."
    Dir.mktmpdir('robolectric-dependencies') do |dir|
      run_cmd("cd #{dir}; jar xvf #{archive} > /dev/null")
      @android_home.install(group_id, artifact_id, version, Pathname(dir) + "classes.jar", sdk_package)
      block.call(dir) if block_given?
    end
  end

  def concat_maven_file_segments(group_id, artifact_id, version, extension)
    # Also don't move further if we have invalid parameters
    if group_id.to_s == '' || artifact_id.to_s == '' || version.to_s == '' || extension.to_s == ''
      raise ArgumentError, "Group ID, Artifact ID, Version, and/or Extension arguments are invalid. Please check your inputs."
    end

    @path + group_id.gsub(/\./, '/') + artifact_id + version + "#{artifact_id}-#{version}.#{extension}"
  end
end

class AndroidHome
  def initialize(base_dir, m2_repo)
    raise "Nothing found at #{base_dir}" unless base_dir.exist?
    @base_dir = base_dir
    @m2_repo = m2_repo
  end

  def base_dir
    @base_dir
  end

  def android_repo
    @android_repo ||= AndroidRepo.new(self, "android", @m2_repo)
  end

  def google_repo
    @google_repo ||= AndroidRepo.new(self, "google", @m2_repo)
  end

  def install(group_id, artifact_id, version, file, sdk_package)
    if @m2_repo.contains?(group_id, artifact_id, version)
      puts "Found #{group_id}:#{artifact_id}:#{version} already installed."
    else
      unless file.exist?
        puts "Installing #{sdk_package}..."
        run_cmd("echo y | #{base_dir.to_s}/tools/android update sdk -s --no-ui --all --filter #{sdk_package}")

        raise "Failed to find #{file}!" unless file.exist?
      end
    end

    @m2_repo.install(group_id, artifact_id, version, file)
  end

  def install_stubs(group_id, artifact_id, api)
    path = base_dir + "platforms/android-#{api}/android.jar"
    install(group_id, artifact_id, api, path, "android-#{api}")
  end

  def install_map(group_id, artifact_id, api, revision)
    dir = base_dir + "add-ons/addon-google_apis-google-#{api}"
    jar_path = dir + "libs/maps.jar"

    # unless jar_path.exist?
    #   puts "#{group_id}:#{artifact_id} not found!"
    #   puts "Make sure that 'Google APIs' is up to date in the SDK manager for API #{api}."
    #   exit 1
    # end

    # revision_match = File.read("#{dir}/manifest.ini").match(/^revision=(\d+)$/)
    # if revision_match.nil?
    #   puts "Manifest file missing revision number."
    #   puts "Make sure that 'Google APIs' is up to date in the SDK manager for API #{api}."
    # end
    # manifest_revision = revision_match[1].strip
    # if manifest_revision != revision
    #   puts "#{group_id}:#{artifact_id} is an incompatible revision!"
    #   puts "Make sure that 'Google APIs' is up to date in the SDK manager for API #{api}. Expected revision #{revision} but was #{manifest_revision}."
    #   exit 1
    # end

    puts "Installing Maps API #{group_id}:#{artifact_id}, API #{api}, revision #{revision}."
    install(group_id, artifact_id, "#{api}_r#{revision}", jar_path, "addon-google_apis-google-#{api}")
  end
 end

class Artifact
  def initialize(group_id, artifact_id, versions: [])

  end
end

unless ENV.has_key?('ANDROID_HOME')
  $stderr.puts "Make sure that the 'ANDROID_HOME' Environment Variable is properly set in your development environment pointing to your SDK installation directory."
  exit 1
end

m2_dir = ENV['M2_REPO'] || File.expand_path("~/.m2/repository")
$stderr.puts "No maven local repository found at #{m2_dir}" unless File.exist?(m2_dir)

m2_repo = M2Repo.new(Pathname(m2_dir))
android_home = AndroidHome.new(Pathname(ENV['ANDROID_HOME']), m2_repo)

android_repo=android_home.android_repo
google_repo=android_home.google_repo

# Mavenize all dependencies

android_home.install_stubs("com.google.android", "android-stubs", "23")

# Maps API maven constants
android_home.install_map("com.google.android.maps", "maps", "23", "1")

[
    "1.0.0", # trailing version
    "1.0.1"
].each do |version|
  android_repo.install_aar("com.android.support", "multidex", version, :none)
end

# Android Support libraries
[
    "23.1.1", # trailing version
    "23.2.0"
].each do |version|
  android_repo.install_aar("com.android.support", "appcompat-v7", version, "extra-android-m2repository")
  android_repo.install_aar("com.android.support", "support-v4", version, "extra-android-m2repository") do |dir|
    android_repo.install_jar("com.android.support", "internal_impl", version, "#{dir}/libs/#{"internal_impl"}-#{version}.jar")
  end
end

# Play Services 6.5.87 version constants, which pulls in all of the play-services
# submodules in classes.jar
google_repo.install_aar("com.google.android.gms", "play-services", "6.5.87", "extra-google-google_play_services")

# Play Services Base and Basement modules, version 8.4.0 (plus trailing version)
# Current "play-services" artifact no longer references each sub-module directly. When you
# Extract its AAR, it only contains a manifest and blank res folder.
#
# As a result, we now have to install "play-services-base" and "play-services-basement"
# separately and use those versions instead.
[
    "8.3.0", # trailing version
    "8.4.0"
].each do |version|
  google_repo.install_aar("com.google.android.gms", "play-services-basement", version, "extra-google-google_play_services")
  google_repo.install_aar("com.google.android.gms", "play-services-base", version, "extra-google-google_play_services")
end

