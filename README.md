# ServerInstances

Dynamically deploy servers to your bungeecord proxy. 

## Gradle
Latest version can be found at https://github.com/Skungee/ServerInstances/packages

In your `build.gradle` add: 
```groovy
repositories {
	maven {
		url 'https://maven.pkg.github.com/Skungee/ServerInstances/'
		credentials {
			username = "<INSERT USERNAME>"
			password = project.findProperty("gpr.key") ?: System.getenv("GITHUB_PACKAGES_KEY")
		}
	}
}

dependencies {
	compile (group: 'com.skungee', name: 'serverinstances', version: 'INSERT VERSION')
}
```
Getting a Github token:

1.) Go into your account settings on Github and create a personal token with the read:packages scope checked.

2.) Generate that key, and now go add a System Environment Variable named GITHUB_PACKAGES_KEY
or set the gradle property "gpr.key" to your key.

3.) Restart system or if using Chocolatey type `refreshenv`

Note: you can just directly put your token as the password, but we highly discourage that.

## Compiling from source
```sh
git clone https://github.com/Skungee/ServerInstances
cd ServerInstances
gradlew build
```

The output jars will be found in the `build/libs` directory of each respective folder

## Usage
```java
public class ExamplePlugin extends JavaPlugin {

	private static ExamplePlugin instance;
	private ServerInstances serverInstances;

	@Override
	public void onEnable() {
		instance = this;
		try {
			serverInstances = new ServerInstances(this);
		} catch (ClassNotFoundException | SQLException | IOException e) {
			e.printStackTrace();
			return;
		}
	}

	public static ExamplePlugin getInstance() {
		return instance;
	}

	public ServerInstances getServerInstances() {
		return serverInstances;
	}

}

```
```java
ExamplePlugin instance = ExamplePlugin.getInstance();
ServerInstances serverInstances = instance.getServerInstances();

String templateName = "Hub";

Optional<Template> optional = serverInstances.getTemplates().stream()
		.filter(template -> template.getName().equals(templateName))
		.findFirst();
if (!optional.isPresent())
	return;
try {
	serverInstances.createInstance(optional.get());
} catch (IOException | IllegalAccessException e) {
	e.printStackTrace();
}
```

## Configurations and templates

Every Bungeecord Plugin that uses ServerInstances must have a serverinstances-configuration.yml shaded into it's resource.
https://github.com/Skungee/ServerInstances/blob/master/src/main/resources/serverinstances-configuration.yml

This allows ServerInstances to read how you want to configure the library.
This configuration will then be loaded into the plugins folder under "ServerInstances" which from there you can edit.

Every template must have the following configuration in it's main directory (where the server jar is located)
https://github.com/Skungee/ServerInstances/blob/master/src/main/resources/template-configuration.yml

And lastly the template needs to have the ServerInstancesBootloader jar in the plugins folder to allow for ServerInstances on the Bungeecord to control the Spigot server.
https://github.com/Skungee/ServerInstancesBootloader

We will extend the API for server management from the Bungeecord side with this bootloader over time.
