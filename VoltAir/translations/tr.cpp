#include "tr.h"
#include <QFile>
#include <QTextStream>

TR* TR::sInstance = nullptr;

TR::TR(QObject *parent) : QObject(parent)
{
    _currentLanguage = "";
    _dictionary.clear();
    _initialized = false;
}

void TR::parseFileLine(const QString &line)
{
    QStringList list = line.split('=');
    QString key = list.first();
    QString value = list.last();
    _dictionary[key] = value;
}

void TR::setCurrentLanguage(QString language)
{
    if (_currentLanguage != language)
    {
        _currentLanguage = language;
        _initialized = false;
    }
}

void TR::loadDictionary()
{
    //_dictionary.clear();
    QString filename = ":/translations/values-" + _currentLanguage + ".properties";
    QFile file(filename);
    if (file.open(QIODevice::ReadOnly | QIODevice::Text))
    {
        QTextStream in(&file);
        while (!in.atEnd())
        {
            QString line = in.readLine();
            parseFileLine(line);
        }
        file.close();
    }
    _initialized = true;
}

QString TR::value(const QString &key)
{
    if (!_initialized)
    {
        loadDictionary();
    }
    return _dictionary.contains(key) ? _dictionary[key] : QString("TBT: %1").arg(key);
}

TR* TR::getInstance()
{
    if (!sInstance)
    {
        sInstance = new TR();
    }
    return sInstance;
}
