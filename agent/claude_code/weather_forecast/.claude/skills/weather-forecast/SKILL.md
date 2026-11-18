---
name: weather-forecast
description: Find tomorrow's weather forecast for a city or a country on the internet and report it. When the input is not a real city or country, report that the region does not exist and give tomorrow's forecast for Tallinn instead. Use when a weather forecast is requested.
argument-hint: "<city or country>"
allowed-tools: WebSearch WebFetch
---

# Weather forecast for tomorrow

Location: $ARGUMENTS

## 1. Check the location

Decide whether the location is a real city or a real country. Spelling mistakes, other languages, and
common abbreviations of a real place count as that place (`Tallin`, `München`, `NYC`, `UK`). Anything else
is not a location: nonsense, a fictional place, a person, an object, an empty value, or several places at
once.

When unsure, search the web for the name. If no city or country of that name exists, it is not a location.

- Real city or country → forecast it.
- Not a location → forecast Tallinn, Estonia, and start the answer with the notice in section 3.

## 2. Find the forecast

1. Determine tomorrow's date in the location's time zone.
2. Search the web for tomorrow's forecast there. For a country, use its capital and say so.
3. Fetch the forecast page of at least one established weather service (national meteorological service,
   yr.no, weather.com, AccuWeather, timeanddate.com, or similar) and read the values for tomorrow's date.
   Search snippets alone are not enough.
4. If two sources disagree a lot, give the values of the one you trust more and mention the difference.

## 3. Answer

Write in English, in this format:

```markdown
**<City, Country> — <weekday, date of tomorrow>**

- **Conditions:** <e.g. cloudy, light rain in the afternoon>
- **Temperature:** <min> … <max> °C
- **Precipitation:** <chance and/or amount>
- **Wind:** <direction, speed in m/s>

Source: <service name> (<URL>)
```

For a country, write `<Capital>, <Country> (capital)` as the place. Leave out a line the source does not
provide instead of guessing it.

When the location was not a real city or country, put this line first, followed by the Tallinn forecast:

```markdown
Region "<location as given>" does not exist. Weather in Tallinn tomorrow:
```

If no source could be fetched, say that the forecast could not be found and why, instead of a forecast.
