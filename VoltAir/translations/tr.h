#ifndef TR_H
#define TR_H

#include <QObject>
#include <QMap>

class TR : public QObject
{
    Q_OBJECT
public:
    Q_INVOKABLE void setCurrentLanguage(QString language);
    Q_INVOKABLE QString getCurrentLanguage() { return _currentLanguage; };

    Q_INVOKABLE QString value(const QString &key);

    static TR* getInstance();
private:
    TR(QObject* parent = nullptr);

    void loadDictionary();
    void parseFileLine(const QString &line);

    QMap<QString, QString> _dictionary;
    QString _currentLanguage;
    bool _initialized;
    static TR* sInstance;
};

#endif // TR_H
