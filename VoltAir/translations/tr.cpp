#include "tr.h"

TR* TR::sInstance = nullptr;

TR::TR(QObject *parent) : QObject(parent)
{
}

void TR::loadDictionary(const QString &language)
{
    //TODO: implement loadDictionary
}

QString TR::value(const QString &key) const
{
    return _dictionary.contains(key) ? _dictionary[key] : key;
}

TR* TR::getInstance()
{
    if (!sInstance)
    {
        sInstance = new TR();
    }
    return sInstance;
}
