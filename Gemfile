source "https://rubygems.org"

gem "fastlane", '>= 2.219.0'
gem 'fastlane-plugin-firebase_app_distribution', '>= 1.0.0' # Or latest version

plugins_path = File.join(File.dirname(__FILE__), 'fastlane', 'Pluginfile')
eval_gemfile(plugins_path) if File.exist?(plugins_path)
