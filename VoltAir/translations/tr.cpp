#include "tr.h"
#include <QFile>
#include <QTextStream>

TR* TR::sInstance = nullptr;

TR::TR(QObject *parent) : QObject(parent)
{
    _currentLanguage = "";
    _dictionary.clear();
}

void TR::parseFileLine(const QString &line)
{
    QStringList list = line.split('=');
    QString key = list.first();
    QString value = list.last();
    _dictionary[key] = value;
}

void TR::loadDictionary(const QString &language)
{
    //_dictionary.clear();
    QString filename = QString(":/translations/values-%1.properties").arg(language);
    QFile file(filename);
    if (file.open(QIODevice::ReadOnly | QIODevice::Text))
    {
        QTextStream in(&file);
        while (!in.atEnd())
        {
            QString line = in.readLine();
            parseFileLine(line);
        }
    }
}

QString TR::value(const QString &key) const
{
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
